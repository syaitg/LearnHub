package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.enums.PracticeAnswerStatus;
import com.tianji.exam.enums.PracticeSessionStatus;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import com.tianji.exam.service.PracticeAiReviewPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主观题 AI 评估持久化服务实现
 */
@Service
@RequiredArgsConstructor
public class PracticeAiReviewPersistenceServiceImpl implements PracticeAiReviewPersistenceService {

    private final PracticeAnswerMapper answerMapper;
    private final PracticeSessionMapper sessionMapper;

    /**
     * 原子领取指定执行代际的待执行 AI 评估答案
     *
     * @param sessionId 练习会话 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     * @return 本次成功领取的答案
     */
    @Override
    @Transactional
    public List<PracticeAnswer> claimPendingAnswers(Long sessionId, int expectedRetryCount) {
        PracticeSession session = lockSession(sessionId);
        if (!hasExecutionAuthority(session, expectedRetryCount)) {
            return List.of();
        }
        List<Long> pendingIds = answerMapper.selectList(Wrappers.<PracticeAnswer>lambdaQuery()
                        .select(PracticeAnswer::getId)
                        .eq(PracticeAnswer::getSessionId, sessionId)
                        .eq(PracticeAnswer::getStatus, PracticeAnswerStatus.PENDING_AI_REVIEW.name()))
                .stream()
                .map(PracticeAnswer::getId)
                .toList();
        if (pendingIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        int claimed = answerMapper.update(null, Wrappers.<PracticeAnswer>lambdaUpdate()
                .in(PracticeAnswer::getId, pendingIds)
                .eq(PracticeAnswer::getStatus, PracticeAnswerStatus.PENDING_AI_REVIEW.name())
                .set(PracticeAnswer::getStatus, PracticeAnswerStatus.AI_REVIEWING.name())
                .set(PracticeAnswer::getUpdateTime, now));
        if (claimed != pendingIds.size()) {
            throw new IllegalStateException("主观题 AI 评估任务领取数量不一致");
        }

        List<PracticeAnswer> claimedAnswers = answerMapper.selectList(Wrappers.<PracticeAnswer>lambdaQuery()
                .in(PracticeAnswer::getId, pendingIds)
                .eq(PracticeAnswer::getStatus, PracticeAnswerStatus.AI_REVIEWING.name())
                .orderByAsc(PracticeAnswer::getPracticeQuestionId));
        if (claimedAnswers.size() != claimed) {
            throw new IllegalStateException("主观题 AI 评估任务读取数量不一致");
        }
        return claimedAnswers;
    }

    /**
     * 保存指定执行代际的所有 AI 评估结果，并刷新练习会话汇总状态
     *
     * @param sessionId 练习会话 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     * @param answers 已填充 AI 评估结果的答案
     */
    @Override
    @Transactional
    public void completeReview(Long sessionId, int expectedRetryCount, List<PracticeAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        PracticeSession session = lockSession(sessionId);
        if (!hasExecutionAuthority(session, expectedRetryCount)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PracticeAnswer result : answers) {
            PracticeAnswer answer = answerMapper.selectById(result.getId());
            validateReviewingAnswer(sessionId, answer);
            copyReviewResult(result, answer, now);
            if (answerMapper.updateById(answer) != 1) {
                throw new IllegalStateException("保存主观题 AI 评估结果失败");
            }
        }
        refreshSessionSummary(sessionId, now);
    }

    /**
     * 将指定执行代际领取后仍在评估中的答案标记为失败
     *
     * @param sessionId 练习会话 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     * @param failureReason 失败原因
     */
    @Override
    @Transactional
    public void failReview(Long sessionId, int expectedRetryCount, String failureReason) {
        PracticeSession session = lockSession(sessionId);
        if (!hasExecutionAuthority(session, expectedRetryCount)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int failedCount = answerMapper.update(null, Wrappers.<PracticeAnswer>lambdaUpdate()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .eq(PracticeAnswer::getStatus, PracticeAnswerStatus.AI_REVIEWING.name())
                .set(PracticeAnswer::getStatus, PracticeAnswerStatus.AI_REVIEW_FAILED.name())
                .set(PracticeAnswer::getAiFailureReason, failureReason)
                .set(PracticeAnswer::getEvaluatedTime, now)
                .set(PracticeAnswer::getUpdateTime, now));
        if (failedCount > 0) {
            refreshSessionSummary(sessionId, now);
        }
    }

    /**
     * 仅将截止时间前仍在评估中的答案标记为失败，避免误伤新领取的任务
     *
     * @param sessionId 练习会话 ID
     * @param cutoff 更新时间截止点
     * @param failureReason 失败原因
     * @return 标记失败的答案数量
     */
    @Override
    @Transactional
    public int failTimedOutReview(Long sessionId, LocalDateTime cutoff, String failureReason) {
        lockSession(sessionId);
        LocalDateTime now = LocalDateTime.now();
        int failedCount = answerMapper.update(null, Wrappers.<PracticeAnswer>lambdaUpdate()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .eq(PracticeAnswer::getStatus, PracticeAnswerStatus.AI_REVIEWING.name())
                .lt(PracticeAnswer::getUpdateTime, cutoff)
                .set(PracticeAnswer::getStatus, PracticeAnswerStatus.AI_REVIEW_FAILED.name())
                .set(PracticeAnswer::getAiFailureReason, failureReason)
                .set(PracticeAnswer::getEvaluatedTime, now)
                .set(PracticeAnswer::getUpdateTime, now));
        if (failedCount > 0) {
            refreshSessionSummary(sessionId, now);
        }
        return failedCount;
    }

    /**
     * 锁定并读取练习会话，保证后续状态校验和答案更新处于同一事务
     *
     * @param sessionId 练习会话 ID
     * @return 已锁定的练习会话
     */
    private PracticeSession lockSession(Long sessionId) {
        if (sessionMapper.lockById(sessionId) == null) {
            throw new IllegalStateException("练习会话不存在");
        }
        PracticeSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalStateException("练习会话不存在");
        }
        return session;
    }

    /**
     * 判断当前异步任务是否仍拥有指定练习会话执行代际的写入权
     *
     * @param session 当前练习会话
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     * @return 是否仍拥有执行权
     */
    private boolean hasExecutionAuthority(PracticeSession session, int expectedRetryCount) {
        int currentRetryCount = session.getAiReviewRetryCount() == null ? 0 : session.getAiReviewRetryCount();
        return PracticeSessionStatus.SUBMITTED.name().equals(session.getStatus())
                && currentRetryCount == expectedRetryCount;
    }

    /**
     * 校验答案仍属于当前会话并处于 AI 评估中状态
     *
     * @param sessionId 练习会话 ID
     * @param answer 当前数据库答案
     */
    private void validateReviewingAnswer(Long sessionId, PracticeAnswer answer) {
        if (answer == null || !sessionId.equals(answer.getSessionId())
                || !PracticeAnswerStatus.AI_REVIEWING.name().equals(answer.getStatus())) {
            throw new IllegalStateException("练习答案状态已变化，无法保存 AI 评估结果");
        }
    }

    /**
     * 将内存中的 AI 评估结果复制到当前数据库实体
     *
     * @param result AI 评估结果
     * @param answer 当前数据库答案
     * @param now 当前时间
     */
    private void copyReviewResult(PracticeAnswer result, PracticeAnswer answer, LocalDateTime now) {
        answer.setStatus(PracticeAnswerStatus.AI_REVIEWED.name())
                .setAiSuggestedScore(result.getAiSuggestedScore())
                .setAiTotalScore(result.getAiTotalScore())
                .setMatchedPoints(result.getMatchedPoints())
                .setMissingPoints(result.getMissingPoints())
                .setIncorrectStatements(result.getIncorrectStatements())
                .setImprovementSuggestion(result.getImprovementSuggestion())
                .setConfidence(result.getConfidence())
                .setManualReviewRecommended(result.getManualReviewRecommended())
                .setRubricVersion(result.getRubricVersion())
                .setAiFailureReason(null)
                .setEvaluatedTime(now)
                .setUpdateTime(now);
    }

    /**
     * 重新汇总主观题处理进度、正式得分和练习会话状态
     *
     * @param sessionId 练习会话 ID
     * @param now 当前时间
     */
    private void refreshSessionSummary(Long sessionId, LocalDateTime now) {
        int unfinishedCount = answerMapper.countUnfinishedAiReview(sessionId);
        int reviewedCount = answerMapper.countAiReviewed(sessionId);
        int failedCount = answerMapper.countAiReviewFailed(sessionId);
        int pendingCount = unfinishedCount + reviewedCount + failedCount;
        String status = pendingCount > 0
                ? PracticeSessionStatus.SUBMITTED.name()
                : PracticeSessionStatus.COMPLETED.name();
        int updated = sessionMapper.update(null, Wrappers.<PracticeSession>lambdaUpdate()
                .eq(PracticeSession::getId, sessionId)
                .in(PracticeSession::getStatus,
                        PracticeSessionStatus.SUBMITTED.name(), PracticeSessionStatus.COMPLETED.name())
                .set(PracticeSession::getStatus, status)
                .set(PracticeSession::getFinalScore, answerMapper.sumScoredAnswers(sessionId))
                .set(PracticeSession::getAiReviewPendingCount, pendingCount)
                .set(PracticeSession::getCompletedTime, pendingCount == 0 ? now : null)
                .set(PracticeSession::getUpdateTime, now));
        if (updated != 1) {
            throw new IllegalStateException("练习会话状态异常，无法更新 AI 评估结果");
        }
    }
}