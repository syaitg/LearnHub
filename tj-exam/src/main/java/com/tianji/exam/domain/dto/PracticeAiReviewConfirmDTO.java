package com.tianji.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 主观题 AI 评估人工确认参数
 */
@Data
@Schema(description = "主观题 AI 评估人工确认参数")
public class PracticeAiReviewConfirmDTO {

    @NotNull(message = "答案 ID 不能为空")
    @Schema(description = "练习答案 ID")
    private Long answerId;

    @NotNull(message = "最终得分不能为空")
    @Min(value = 0, message = "最终得分不能小于 0")
    @Max(value = 100, message = "最终得分不能超过 100")
    @Schema(description = "教师确认后的最终得分")
    private Integer finalScore;
}