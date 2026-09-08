package com.tianji.exam.service.impl;

import com.tianji.api.dto.aigc.AiGeneratedQuestionDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.CollUtils;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.AiQuestionDraft;
import com.tianji.exam.enums.AiQuestionBatchStatus;
import com.tianji.exam.enums.AiQuestionDraftStatus;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.mapper.AiQuestionDraftMapper;
import com.tianji.exam.service.AiQuestionGenerationPersistenceService;
import com.tianji.exam.service.IAiQuestionDraftService;
import com.tianji.exam.utils.AiQuestionContentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 题目生成结果持久化服务实现
 */
@Service
@RequiredArgsConstructor
public class AiQuestionGenerationPersistenceServiceImpl implements AiQuestionGenerationPersistenceService {

    private final AiQuestionBatchMapper batchMapper;
    private final AiQuestionDraftMapper draftMapper;
    private final IAiQuestionDraftService draftService;

    /**
     * 在同一事务中完成草稿保存和批次统计更新。
     *
     * @param batchId 出题批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param generated 模型生成的题目列表
     */
    @Override
    @Transactional
    public void persist(Long batchId, int expectedRetryCount, List<AiGeneratedQuestionDTO> generated) {
        AiQuestionBatch batch = lockBatch(batchId);
        if (!AiQuestionBatchStatus.VALIDATING.name().equals(batch.getStatus())
                || batch.getRetryCount() == null
                || batch.getRetryCount() != expectedRetryCount) {
            return;
        }
        if (CollUtils.isEmpty(generated)) {
            throw new BadRequestException("AI 未返回可保存的题目");
        }
        if (batch.getQuestionCount() == null || generated.size() > batch.getQuestionCount()) {
            throw new BadRequestException("AI 返回的题目数量超过本批次请求上限");
        }
        if (draftMapper.countByBatchId(batchId) > 0) {
            throw new BadRequestException("当前批次已经保存过题目草稿");
        }

        List<AiQuestionDraft> drafts = buildDrafts(batch, generated);
        if (!draftService.saveBatch(drafts)) {
            throw new DbException("AI 题目草稿保存失败");
        }
        refreshBatchStatistics(batch, expectedRetryCount, drafts);
    }

    /**
     * 将模型结果转换为经过结构校验和重复检测的草稿列表。
     *
     * @param batch 出题批次
     * @param generated 模型生成的题目列表
     * @return 待保存的题目草稿
     */
    private List<AiQuestionDraft> buildDrafts(AiQuestionBatch batch, List<AiGeneratedQuestionDTO> generated) {
        List<AiQuestionDraft> drafts = new ArrayList<>(generated.size());
        Set<String> currentFingerprints = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        int sequence = 1;
        for (AiGeneratedQuestionDTO question : generated) {
            fillDefaultValues(batch, question);
            AiQuestionDraft draft = toDraft(batch, question, sequence++, now);
            validateDraft(batch, question, draft, currentFingerprints);
            drafts.add(draft);
        }
        return drafts;
    }

    /**
     * 为模型未返回的难度和分值补充批次默认值。
     *
     * @param batch 出题批次
     * @param question 模型生成的题目
     */
    private void fillDefaultValues(AiQuestionBatch batch, AiGeneratedQuestionDTO question) {
        if (question == null) {
            return;
        }
        if (question.getDifficulty() == null) {
            question.setDifficulty(batch.getDifficulty());
        }
        if (question.getScore() == null) {
            question.setScore(batch.getScore());
        }
    }

    /**
     * 校验题目内容并设置草稿状态。
     *
     * @param batch 出题批次
     * @param question 模型生成的题目
     * @param draft 待保存草稿
     * @param currentFingerprints 当前批次已出现的内容指纹
     */
    private void validateDraft(AiQuestionBatch batch, AiGeneratedQuestionDTO question, AiQuestionDraft draft,
                               Set<String> currentFingerprints) {
        AiQuestionContentValidator.ValidationResult validation = AiQuestionContentValidator.validate(question);
        if (!validation.valid()) {
            draft.setStatus(AiQuestionDraftStatus.INVALID.name())
                    .setValidationMessage(validation.message());
            return;
        }
        if (CollUtils.isEmpty(batch.getQuestionTypes()) || !batch.getQuestionTypes().contains(question.getType())) {
            draft.setStatus(AiQuestionDraftStatus.INVALID.name())
                    .setValidationMessage("AI 返回了当前批次未请求的题型");
            return;
        }

        String fingerprint = AiQuestionContentValidator.fingerprint(question.getType(), question.getName(), question.getOptions());
        draft.setName(question.getName())
                .setType(question.getType())
                .setDifficulty(question.getDifficulty())
                .setScore(question.getScore())
                .setOptions(question.getOptions())
                .setAnswer(question.getAnswer())
                .setAnalysis(question.getAnalysis())
                .setKnowledgePoints(question.getKnowledgePoints())
                .setContentFingerprint(fingerprint);

        boolean duplicate = !currentFingerprints.add(fingerprint)
                || draftMapper.countPotentialDuplicate(fingerprint, batch.getId(), batch.getCourseId(),
                batch.getScopeType(), batch.getScopeId(), batch.getTargetBizId()) > 0;
        if (duplicate) {
            draft.setStatus(AiQuestionDraftStatus.DUPLICATE.name())
                    .setValidationMessage("在相同出题范围或目标练习中检测到重复题目");
            return;
        }
        draft.setStatus(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
    }

    /**
     * 将模型题目转换为草稿实体。
     *
     * @param batch 出题批次
     * @param question 模型生成的题目
     * @param sequence 批次内顺序
     * @param now 创建时间
     * @return 草稿实体
     */
    private AiQuestionDraft toDraft(AiQuestionBatch batch, AiGeneratedQuestionDTO question,
                                    int sequence, LocalDateTime now) {
        AiQuestionDraft draft = new AiQuestionDraft()
                .setBatchId(batch.getId())
                .setSequenceNo(sequence)
                .setCreater(batch.getCreater())
                .setUpdater(batch.getCreater())
                .setCreateTime(now)
                .setUpdateTime(now);
        if (question != null) {
            draft.setName(question.getName())
                    .setType(question.getType())
                    .setDifficulty(question.getDifficulty())
                    .setScore(question.getScore())
                    .setOptions(question.getOptions())
                    .setAnswer(question.getAnswer())
                    .setAnalysis(question.getAnalysis())
                    .setKnowledgePoints(question.getKnowledgePoints());
        }
        return draft;
    }

    /**
     * 根据草稿状态刷新批次统计和后续审核状态。
     *
     * @param batch 出题批次
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param drafts 已校验草稿列表
     */
    private void refreshBatchStatistics(AiQuestionBatch batch, int expectedRetryCount,
                                        List<AiQuestionDraft> drafts) {
        int validCount = 0;
        int duplicateCount = 0;
        for (AiQuestionDraft draft : drafts) {
            if (AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(draft.getStatus())) {
                validCount++;
            } else if (AiQuestionDraftStatus.DUPLICATE.name().equals(draft.getStatus())) {
                duplicateCount++;
            }
        }
        String status = validCount > 0
                ? AiQuestionBatchStatus.PENDING_CONFIRMATION.name()
                : AiQuestionBatchStatus.INVALID.name();
        int updated = batchMapper.completeValidation(batch.getId(), expectedRetryCount, status,
                drafts.size(), validCount, duplicateCount, LocalDateTime.now());
        if (updated != 1) {
            throw new BadRequestException("批次状态已发生变化，无法保存生成结果");
        }
    }

    /**
     * 锁定并读取出题批次。
     *
     * @param batchId 出题批次 ID
     * @return 出题批次
     */
    private AiQuestionBatch lockBatch(Long batchId) {
        if (batchMapper.lockById(batchId) == null) {
            throw new BadRequestException("AI 出题批次不存在");
        }
        AiQuestionBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BadRequestException("AI 出题批次不存在");
        }
        return batch;
    }
}