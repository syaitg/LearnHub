package com.tianji.exam.domain.event;

/**
 * 练习主观题 AI 评估请求事件
 *
 * @param sessionId 练习会话 ID
 * @param userId 学生用户 ID
 * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
 */
public record PracticeSubjectiveReviewRequestedEvent(Long sessionId, Long userId, int expectedRetryCount) {
}