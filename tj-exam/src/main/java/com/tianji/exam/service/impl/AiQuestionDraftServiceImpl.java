package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.aigc.AiGeneratedQuestionDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.dto.AiQuestionDraftUpdateDTO;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.AiQuestionDraft;
import com.tianji.exam.enums.AiQuestionBatchStatus;
import com.tianji.exam.enums.AiQuestionDraftStatus;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.mapper.AiQuestionDraftMapper;
import com.tianji.exam.service.IAiQuestionDraftService;
import com.tianji.exam.utils.AiQuestionContentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 题目草稿服务实现
 */
@Service
@RequiredArgsConstructor
public class AiQuestionDraftServiceImpl extends ServiceImpl<AiQuestionDraftMapper, AiQuestionDraft>
        implements IAiQuestionDraftService {

    private final AiQuestionBatchMapper batchMapper;

    /**
     * 删除一条尚未发布的 AI 题目草稿，并刷新所属批次统计。
     *
     * @param id 草稿 ID
     */
    @Override
    @Transactional
    public void deleteDraft(Long id) {
        AiQuestionDraft draft = lockOwnedDraft(id);
        if (AiQuestionDraftStatus.PUBLISHED.name().equals(draft.getStatus())
                || draft.getQuestionId() != null) {
            throw new BadRequestException("已发布的题目草稿不能删除");
        }
        AiQuestionBatch batch = getBatch(draft.getBatchId());
        requireDraftOperationBatchStatus(batch, "当前批次状态不允许删除题目草稿",
                AiQuestionBatchStatus.PENDING_CONFIRMATION,
                AiQuestionBatchStatus.PARTIALLY_PUBLISHED,
                AiQuestionBatchStatus.INVALID);
        if (!removeById(id)) {
            throw new DbException("AI 题目草稿删除失败");
        }
        recalculateBatch(batch.getId());
    }

    /**
     * 编辑并重新校验 AI 题目草稿。
     *
     * @param id 草稿 ID
     * @param dto 编辑参数
     */
    @Override
    @Transactional
    public void updateDraft(Long id, AiQuestionDraftUpdateDTO dto) {
        AiQuestionDraft draft = lockOwnedDraft(id);
        if (AiQuestionDraftStatus.PUBLISHED.name().equals(draft.getStatus())
                || AiQuestionDraftStatus.REJECTED.name().equals(draft.getStatus())) {
            throw new BadRequestException("已发布或已驳回的草稿不能编辑");
        }
        AiQuestionBatch batch = getBatch(draft.getBatchId());
        requireDraftOperationBatchStatus(batch, "当前批次状态不允许编辑题目草稿",
                AiQuestionBatchStatus.PENDING_CONFIRMATION,
                AiQuestionBatchStatus.PARTIALLY_PUBLISHED,
                AiQuestionBatchStatus.INVALID);

        AiGeneratedQuestionDTO question = new AiGeneratedQuestionDTO();
        question.setName(dto.getName());
        question.setType(dto.getType());
        question.setDifficulty(dto.getDifficulty());
        question.setScore(dto.getScore());
        question.setOptions(dto.getOptions());
        question.setAnswer(dto.getAnswer());
        question.setAnalysis(dto.getAnalysis());
        question.setKnowledgePoints(dto.getKnowledgePoints());

        AiQuestionContentValidator.ValidationResult validation = AiQuestionContentValidator.validate(question);
        String status;
        String validationMessage = null;
        String fingerprint = null;
        if (!validation.valid()) {
            status = AiQuestionDraftStatus.INVALID.name();
            validationMessage = validation.message();
        } else if (batch.getQuestionTypes() == null || !batch.getQuestionTypes().contains(question.getType())) {
            status = AiQuestionDraftStatus.INVALID.name();
            validationMessage = "题型不在当前批次请求的题型范围内";
        } else {
            fingerprint = AiQuestionContentValidator.fingerprint(question.getType(), question.getName(), question.getOptions());
            boolean duplicateInBatch = lambdaQuery()
                    .eq(AiQuestionDraft::getBatchId, batch.getId())
                    .eq(AiQuestionDraft::getContentFingerprint, fingerprint)
                    .ne(AiQuestionDraft::getId, id)
                    .notIn(AiQuestionDraft::getStatus,
                            AiQuestionDraftStatus.REJECTED.name(), AiQuestionDraftStatus.INVALID.name())
                    .count() > 0;
            boolean duplicateElsewhere = baseMapper.countPotentialDuplicate(fingerprint, batch.getId(),
                    batch.getCourseId(), batch.getScopeType(), batch.getScopeId(), batch.getTargetBizId()) > 0;
            if (duplicateInBatch || duplicateElsewhere) {
                status = AiQuestionDraftStatus.DUPLICATE.name();
                validationMessage = "在相同出题范围或目标练习中检测到重复题目";
            } else {
                status = AiQuestionDraftStatus.PENDING_CONFIRMATION.name();
            }
        }

        AiQuestionDraft updatedDraft = new AiQuestionDraft()
                .setId(id)
                .setName(question.getName())
                .setType(question.getType())
                .setDifficulty(question.getDifficulty())
                .setScore(question.getScore())
                .setOptions(question.getOptions())
                .setAnswer(question.getAnswer())
                .setAnalysis(question.getAnalysis())
                .setKnowledgePoints(question.getKnowledgePoints())
                .setContentFingerprint(fingerprint)
                .setStatus(status)
                .setValidationMessage(validationMessage)
                .setUpdater(requireCurrentUser())
                .setUpdateTime(LocalDateTime.now());
        int updated = baseMapper.updateDraftContent(updatedDraft);
        if (updated != 1) {
            throw new DbException("AI 题目草稿更新失败");
        }
        recalculateBatch(batch.getId());
    }

    /**
     * 确认 AI 题目草稿。
     *
     * @param id 草稿 ID
     */
    @Override
    @Transactional
    public void confirm(Long id) {
        AiQuestionDraft draft = lockOwnedDraft(id);
        AiQuestionBatch batch = getBatch(draft.getBatchId());
        requireDraftOperationBatchStatus(batch, "当前批次状态不允许确认题目草稿",
                AiQuestionBatchStatus.PENDING_CONFIRMATION,
                AiQuestionBatchStatus.PARTIALLY_PUBLISHED);
        if (AiQuestionDraftStatus.CONFIRMED.name().equals(draft.getStatus())
                || AiQuestionDraftStatus.PUBLISHED.name().equals(draft.getStatus())) {
            return;
        }
        if (!AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(draft.getStatus())) {
            throw new BadRequestException("只有等待确认的有效草稿才能确认");
        }
        Long userId = requireCurrentUser();
        boolean updated = updateById(new AiQuestionDraft()
                .setId(id)
                .setStatus(AiQuestionDraftStatus.CONFIRMED.name())
                .setConfirmedBy(userId)
                .setConfirmedTime(LocalDateTime.now())
                .setUpdater(userId)
                .setUpdateTime(LocalDateTime.now()));
        if (!updated) {
            throw new DbException("AI 题目草稿确认失败");
        }
        recalculateBatch(draft.getBatchId());
    }

    /**
     * 驳回 AI 题目草稿。
     *
     * @param id 草稿 ID
     * @param reason 驳回原因
     */
    @Override
    @Transactional
    public void reject(Long id, String reason) {
        AiQuestionDraft draft = lockOwnedDraft(id);
        AiQuestionBatch batch = getBatch(draft.getBatchId());
        requireDraftOperationBatchStatus(batch, "当前批次状态不允许驳回题目草稿",
                AiQuestionBatchStatus.PENDING_CONFIRMATION,
                AiQuestionBatchStatus.PARTIALLY_PUBLISHED,
                AiQuestionBatchStatus.INVALID);
        if (AiQuestionDraftStatus.REJECTED.name().equals(draft.getStatus())) {
            return;
        }
        if (AiQuestionDraftStatus.PUBLISHED.name().equals(draft.getStatus())) {
            throw new BadRequestException("已发布的草稿不能驳回");
        }
        boolean updated = updateById(new AiQuestionDraft()
                .setId(id)
                .setStatus(AiQuestionDraftStatus.REJECTED.name())
                .setValidationMessage(reason == null || reason.isBlank() ? "人工审核驳回" : reason.trim())
                .setConfirmedBy(null)
                .setConfirmedTime(null)
                .setUpdater(requireCurrentUser())
                .setUpdateTime(LocalDateTime.now()));
        if (!updated) {
            throw new DbException("AI 题目草稿驳回失败");
        }
        recalculateBatch(draft.getBatchId());
    }

    /**
     * 查询并锁定当前用户拥有的草稿。
     *
     * @param id 草稿 ID
     * @return 草稿实体
     */
    private AiQuestionDraft lockOwnedDraft(Long id) {
        AiQuestionDraft draft = getRequired(id);
        AiQuestionBatch batch = getBatch(draft.getBatchId());
        if (!requireCurrentUser().equals(batch.getCreater())) {
            throw new BadRequestException("无权操作其他用户创建的题目草稿");
        }
        if (batchMapper.lockById(batch.getId()) == null) {
            throw new BadRequestException("题目批次不存在");
        }
        return getRequired(id);
    }

    /**
     * 查询 AI 题目草稿，不存在时抛出业务异常。
     *
     * @param id 草稿 ID
     * @return 草稿实体
     */
    private AiQuestionDraft getRequired(Long id) {
        AiQuestionDraft draft = getById(id);
        if (draft == null) {
            throw new BadRequestException("题目草稿不存在");
        }
        return draft;
    }

    /**
     * 查询草稿所属出题批次。
     *
     * @param batchId 批次 ID
     * @return 出题批次
     */
    private AiQuestionBatch getBatch(Long batchId) {
        AiQuestionBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BadRequestException("题目批次不存在");
        }
        return batch;
    }

    /**
     * 校验草稿操作对应的批次状态。
     *
     * @param batch 出题批次
     * @param message 状态不允许时的提示
     * @param allowed 允许的批次状态
     */
    private void requireDraftOperationBatchStatus(AiQuestionBatch batch,
                                                  String message,
                                                  AiQuestionBatchStatus... allowed) {
        for (AiQuestionBatchStatus status : allowed) {
            if (status.name().equals(batch.getStatus())) {
                return;
            }
        }
        throw new BadRequestException(message);
    }

    /**
     * 重新统计草稿数量并更新出题批次状态。
     *
     * @param batchId 批次 ID
     */
    private void recalculateBatch(Long batchId) {
        List<AiQuestionDraft> drafts = lambdaQuery().eq(AiQuestionDraft::getBatchId, batchId).list();
        int valid = 0;
        int duplicates = 0;
        int published = 0;
        int pendingOrConfirmed = 0;
        for (AiQuestionDraft draft : drafts) {
            String status = draft.getStatus();
            if (AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(status)
                    || AiQuestionDraftStatus.CONFIRMED.name().equals(status)
                    || AiQuestionDraftStatus.PUBLISHED.name().equals(status)) {
                valid++;
            }
            if (AiQuestionDraftStatus.DUPLICATE.name().equals(status)) {
                duplicates++;
            }
            if (AiQuestionDraftStatus.PUBLISHED.name().equals(status)) {
                published++;
            }
            if (AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(status)
                    || AiQuestionDraftStatus.CONFIRMED.name().equals(status)) {
                pendingOrConfirmed++;
            }
        }
        String batchStatus;
        if (published > 0) {
            batchStatus = pendingOrConfirmed > 0
                    ? AiQuestionBatchStatus.PARTIALLY_PUBLISHED.name()
                    : AiQuestionBatchStatus.PUBLISHED.name();
        } else {
            batchStatus = valid > 0
                    ? AiQuestionBatchStatus.PENDING_CONFIRMATION.name()
                    : AiQuestionBatchStatus.INVALID.name();
        }
        int updated = batchMapper.updateById(new AiQuestionBatch()
                .setId(batchId)
                .setStatus(batchStatus)
                .setTotalCount(drafts.size())
                .setValidCount(valid)
                .setDuplicateCount(duplicates)
                .setPublishedCount(published)
                .setUpdater(requireCurrentUser())
                .setUpdateTime(LocalDateTime.now()));
        if (updated != 1) {
            throw new DbException("AI 出题批次统计刷新失败");
        }
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前用户 ID
     */
    private Long requireCurrentUser() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new UnauthorizedException("请先登录后再审核 AI 题目");
        }
        return userId;
    }
}
