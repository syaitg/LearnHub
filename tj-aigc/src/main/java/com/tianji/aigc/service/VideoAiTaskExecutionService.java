package com.tianji.aigc.service;

/**
 * 视频 AI 任务执行服务
 */
public interface VideoAiTaskExecutionService {

    /**
     * 执行视频转写和内容分析任务
     *
     * @param taskId 任务 ID
     */
    void execute(Long taskId);
}