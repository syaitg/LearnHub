package com.tianji.exam.task;

import com.tianji.exam.config.ExamAiTaskProperties;
import com.tianji.exam.domain.event.AiQuestionGenerationRequestedEvent;
import com.tianji.exam.domain.event.PracticeSubjectiveReviewRequestedEvent;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import com.tianji.exam.service.PracticeAiReviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试 AI 异步任务恢复与超时扫描器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamAiTaskRecoveryScanner {

    private static final String GENERATION_TIMEOUT_REASON = "AI 出题任务执行超时，可稍后重试";
    private static final String REVIEW_TIMEOUT_REASON = "主观题 AI 评估执行超时，可稍后重试";

    private final AiQuestionBatchMapper batchMapper;
    private final PracticeSessionMapper sessionMapper;
    private final PracticeAnswerMapper answerMapper;
    private final PracticeAiReviewPersistenceService reviewPersistenceService;
    private final ExamAiTaskProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 定期重新投递长时间停留在创建状态的 AI 出题批次和已提交练习会话。
     */
    @Scheduled(fixedDelayString = "${tj.ai.exam.recovery-scan-interval-ms:60000}")
    public void republishPendingTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getPendingRecoveryDelay());
        int limit = Math.max(1, properties.getRecoveryBatchSize());

        LocalDateTime claimTime = LocalDateTime.now();
        List<AiQuestionBatch> batches = batchMapper.selectStaleCreatedTasks(cutoff, limit);
        int batchCount = 0;
        if (batches != null) {
            for (AiQuestionBatch batch : batches) {
                if (batch == null || batch.getId() == null) {
                    continue;
                }
                int retryCount = batch.getRetryCount() == null ? 0 : batch.getRetryCount();
                if (batchMapper.claimStaleCreatedTask(batch.getId(), retryCount, cutoff, claimTime) != 1) {
                    continue;
                }
                eventPublisher.publishEvent(new AiQuestionGenerationRequestedEvent(batch.getId()));
                batchCount++;
            }
        }

        List<PracticeSession> sessions = sessionMapper.selectStaleSubmittedSessions(cutoff, limit);
        int sessionCount = 0;
        if (sessions != null) {
            for (PracticeSession session : sessions) {
                if (session == null || session.getId() == null || session.getUserId() == null) {
                    continue;
                }
                int retryCount = session.getAiReviewRetryCount() == null ? 0 : session.getAiReviewRetryCount();
                if (sessionMapper.claimStaleSubmittedSession(
                        session.getId(), retryCount, cutoff, claimTime) != 1) {
                    continue;
                }
                eventPublisher.publishEvent(new PracticeSubjectiveReviewRequestedEvent(
                        session.getId(), session.getUserId(), retryCount));
                sessionCount++;
            }
        }

        if (batchCount > 0 || sessionCount > 0) {
            log.info("本次重新投递 {} 个 AI 出题批次和 {} 个主观题评估会话", batchCount, sessionCount);
        }
    }

    /**
     * 定期将长时间停留在生成或校验状态的 AI 出题批次标记为失败。
     */
    @Scheduled(fixedDelayString = "${tj.ai.exam.generation-timeout-scan-interval-ms:60000}")
    public void markTimedOutGenerationTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(properties.getGenerationTimeout());
        int limit = Math.max(1, properties.getRecoveryBatchSize());
        List<AiQuestionBatch> batches = batchMapper.selectStaleProcessingTasks(cutoff, limit);
        if (batches == null || batches.isEmpty()) {
            return;
        }
        int count = 0;
        for (AiQuestionBatch batch : batches) {
            if (batch == null || batch.getId() == null) {
                continue;
            }
            int retryCount = batch.getRetryCount() == null ? 0 : batch.getRetryCount();
            count += batchMapper.markStaleProcessingFailed(batch.getId(), retryCount, cutoff,
                    GENERATION_TIMEOUT_REASON, now);
        }
        if (count > 0) {
            log.warn("本次共标记 {} 个超时的 AI 出题批次", count);
        }
    }

    /**
     * 定期将长时间停留在 AI 评估中的主观题答案标记为失败。
     */
    @Scheduled(fixedDelayString = "${tj.ai.exam.review-timeout-scan-interval-ms:60000}")
    public void markTimedOutReviewTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(properties.getReviewTimeout());
        int limit = Math.max(1, properties.getRecoveryBatchSize());
        List<Long> sessionIds = answerMapper.selectStaleReviewingSessionIds(cutoff, limit);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }

        int failedCount = 0;
        for (Long sessionId : sessionIds) {
            if (sessionId != null) {
                failedCount += reviewPersistenceService.failTimedOutReview(
                        sessionId, cutoff, REVIEW_TIMEOUT_REASON);
            }
        }
        if (failedCount > 0) {
            log.warn("本次共标记 {} 个超时的主观题 AI 评估答案", failedCount);
        }
    }
}