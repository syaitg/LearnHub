package com.tianji.exam.service.impl;

import com.tianji.exam.domain.event.PracticeSubjectiveReviewRequestedEvent;
import com.tianji.exam.service.PracticeAiReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 主观题 AI 评估事件监听器
 */
@Component
@RequiredArgsConstructor
public class PracticeAiReviewEventListener {

    private final PracticeAiReviewTaskService reviewTaskService;

    /**
     * 在练习提交事务完成后启动 AI 评估任务
     *
     * @param event 主观题 AI 评估请求事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleReviewRequest(PracticeSubjectiveReviewRequestedEvent event) {
        reviewTaskService.reviewAsync(event.sessionId(), event.userId(), event.expectedRetryCount());
    }
}