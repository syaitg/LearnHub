package com.tianji.api.dto.aigc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 智能出题结果
 */
@Data
@Schema(description = "AI 智能出题结果")
public class AiQuestionGenerateResultDTO {
    private List<AiGeneratedQuestionDTO> questions;
}
