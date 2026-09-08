package com.tianji.exam.service;

/**
 * 主观题 AI 异步评估任务服务
 */
public interface PracticeAiReviewTaskService {

    /**
     * 异步评估指定练习会话中的主观题答案
     *
     * @param sessionId 练习会话 ID
     * @param userId 学生用户 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     */
    void reviewAsync(Long sessionId, Long userId, int expectedRetryCount);
}