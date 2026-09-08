package com.tianji.aigc.listener;

import com.tianji.aigc.domain.event.VideoAiTaskRequestedEvent;
import com.tianji.aigc.service.VideoAiTaskExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 视频 AI 任务事件监听器
 */
@Component
@RequiredArgsConstructor
public class VideoAiTaskEventListener {

    private final VideoAiTaskExecutionService executionService;

    /**
     * 在任务事务提交后异步执行视频处理
     *
     * @param event 视频 AI 任务事件
     */
    @Async("videoAiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(VideoAiTaskRequestedEvent event) {
        executionService.execute(event.getTaskId());
    }
}