package com.tianji.exam.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
/**
 * AI 出题批次确认参数
 */
@Data
public class AiQuestionBatchConfirmDTO {
    /** 为空时表示确认该批次下全部待确认草稿。 */
    private List<Long> draftIds;
}
