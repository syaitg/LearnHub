package com.tianji.exam.enums;

/**
 * AI 出题批次状态枚举
 */
public enum AiQuestionBatchStatus {
    CREATED,
    GENERATING,
    VALIDATING,
    PENDING_CONFIRMATION,
    PARTIALLY_PUBLISHED,
    PUBLISHED,
    GENERATION_FAILED,
    INVALID,
    CANCELLED
}
