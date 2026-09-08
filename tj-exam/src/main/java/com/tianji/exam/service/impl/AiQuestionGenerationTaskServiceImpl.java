package com.tianji.exam.service.impl;

import com.tianji.api.client.aigc.AigcTaskClient;
import com.tianji.api.dto.aigc.AiQuestionGenerateRequestDTO;
import com.tianji.api.dto.aigc.AiQuestionGenerateResultDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.service.AiQuestionGenerationPersistenceService;
import com.tianji.exam.service.AiQuestionGenerationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 题目异步生成任务服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiQuestionGenerationTaskServiceImpl implements AiQuestionGenerationTaskService {

    private static final int FAILURE_REASON_MAX_LENGTH = 1000;

    private final AiQuestionBatchMapper batchMapper;
    private final AiQuestionGenerationPersistenceService persistenceService;
    private final AigcTaskClient aigcTaskClient;

    /**
     * 执行 AI 出题任务，并在任务边界统一记录失败状态。
     *
     * @param batchId 出题批次 ID
     */
    @Override
    @Async
    public void generateAsync(Long batchId) {
        AiQuestionBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        int expectedRetryCount = batch.getRetryCount() == null ? 0 : batch.getRetryCount();
        if (!startGeneration(batchId, expectedRetryCount)) {
            return;
        }
        UserContext.setUser(batch.getCreater());
        try {
            AiQuestionGenerateResultDTO result = aigcTaskClient.generateQuestions(toRequest(batch));
            if (result == null || CollUtils.isEmpty(result.getQuestions())) {
                throw new IllegalStateException("AI 服务未返回题目");
            }
            if (batch.getQuestionCount() != null
                    && result.getQuestions().size() != batch.getQuestionCount()) {
                throw new IllegalStateException("AI 返回题目数量与请求数量不一致");
            }
            if (!startValidation(batchId, expectedRetryCount)) {
                return;
            }
            persistenceService.persist(batchId, expectedRetryCount, result.getQuestions());
        } catch (Exception e) {
            log.error("AI 出题批次 {} 生成失败", batchId, e);
            markGenerationFailed(batchId, expectedRetryCount, normalizeFailureReason(e));
        } finally {
            UserContext.removeUser();
        }
    }

    /**
     * 将待生成批次原子更新为生成中，避免同一批次被重复执行。
     *
     * @param batchId 出题批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @return 是否成功获得本次生成执行权
     */
    private boolean startGeneration(Long batchId, int expectedRetryCount) {
        return batchMapper.startGeneration(batchId, expectedRetryCount, LocalDateTime.now()) == 1;
    }

    /**
     * 将生成中的批次原子更新为结构校验中。
     *
     * @param batchId 出题批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @return 是否成功进入校验阶段
     */
    private boolean startValidation(Long batchId, int expectedRetryCount) {
        return batchMapper.startValidation(batchId, expectedRetryCount, LocalDateTime.now()) == 1;
    }

    /**
     * 仅在任务仍处于生成或校验阶段时回写失败状态。
     *
     * @param batchId 出题批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param failureReason 失败原因
     */
    private void markGenerationFailed(Long batchId, int expectedRetryCount, String failureReason) {
        batchMapper.markGenerationFailed(batchId, expectedRetryCount, failureReason, LocalDateTime.now());
    }

    /**
     * 组装发送给 AIGC 服务的出题请求。
     *
     * @param batch 出题批次
     * @return AI 出题请求
     */
    private AiQuestionGenerateRequestDTO toRequest(AiQuestionBatch batch) {
        AiQuestionGenerateRequestDTO request = new AiQuestionGenerateRequestDTO();
        request.setCourseId(batch.getCourseId());
        request.setCourseName(batch.getCourseName());
        request.setScopeType(batch.getScopeType());
        request.setScopeId(batch.getScopeId());
        request.setScopeName(batch.getScopeName());
        request.setScopeSectionIds(batch.getScopeSectionIds());
        request.setScopeSectionNames(batch.getScopeSectionNames());
        request.setSourceType(batch.getSourceType());
        request.setSourceId(batch.getSourceId());
        request.setSourceVersion(batch.getSourceVersion());
        request.setKnowledgePoints(batch.getKnowledgePoints());
        request.setMaterialText(batch.getMaterialText());
        request.setQuestionTypes(batch.getQuestionTypes());
        request.setQuestionCount(batch.getQuestionCount());
        request.setDifficulty(batch.getDifficulty());
        request.setScore(batch.getScore());
        return request;
    }

    /**
     * 截断过长异常信息，避免失败原因超过数据库字段长度。
     *
     * @param exception 任务异常
     * @return 可保存的失败原因
     */
    private String normalizeFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "AI 出题任务执行失败";
        }
        String normalized = message.trim();
        return normalized.length() <= FAILURE_REASON_MAX_LENGTH
                ? normalized
                : normalized.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}