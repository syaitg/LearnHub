package com.tianji.exam.domain.event;

/**
 * AI 出题生成请求事件
 *
 * @param batchId 出题批次 ID
 */
public record AiQuestionGenerationRequestedEvent(Long batchId) {
}