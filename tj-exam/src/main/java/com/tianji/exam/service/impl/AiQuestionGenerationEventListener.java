package com.tianji.exam.service.impl;

import com.tianji.exam.domain.event.AiQuestionGenerationRequestedEvent;
import com.tianji.exam.service.AiQuestionGenerationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AI 出题生成事件监听器
 */
@Component
@RequiredArgsConstructor
public class AiQuestionGenerationEventListener {

    private final AiQuestionGenerationTaskService generationTaskService;

    /**
     * 在批次事务提交后异步执行题目生成，避免异步线程读取到未提交数据。
     *
     * @param event AI 出题生成请求事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleGenerationRequest(AiQuestionGenerationRequestedEvent event) {
        generationTaskService.generateAsync(event.batchId());
    }
}