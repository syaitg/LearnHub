package com.tianji.exam.service;

/**
 * AI 题目异步生成任务服务
 */
public interface AiQuestionGenerationTaskService {

    /**
     * 异步生成并保存 AI 题目草稿
     */
    void generateAsync(Long batchId);
}
