package com.tianji.exam.service;

import com.tianji.exam.domain.po.PracticeAnswer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主观题 AI 评估持久化服务
 */
public interface PracticeAiReviewPersistenceService {

    /**
     * 原子领取指定执行代际的待评估答案
     *
     * @param sessionId 练习会话 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     * @return 本次成功领取的答案
     */
    List<PracticeAnswer> claimPendingAnswers(Long sessionId, int expectedRetryCount);

    /**
     * 保存指定执行代际的 AI 评估结果并完成练习会话
     *
     * @param sessionId 练习会话 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     * @param answers 已填充 AI 评估结果的答案
     */
    void completeReview(Long sessionId, int expectedRetryCount, List<PracticeAnswer> answers);

    /**
     * 将指定执行代际仍在评估中的答案标记为失败
     *
     * @param sessionId 练习会话 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     * @param failureReason 失败原因
     */
    void failReview(Long sessionId, int expectedRetryCount, String failureReason);

    /**
     * 将超过截止时间仍在评估中的答案标记为失败。
     *
     * @param sessionId 练习会话 ID
     * @param cutoff 更新时间截止点
     * @param failureReason 失败原因
     * @return 标记失败的答案数量
     */
    int failTimedOutReview(Long sessionId, LocalDateTime cutoff, String failureReason);
}