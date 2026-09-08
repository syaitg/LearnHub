package com.tianji.api.dto.aigc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;


/**
 * 主观题 AI 评估结果
 */
@Data
@Schema(description = "主观题 AI 评估结果")
public class SubjectiveGradingResultDTO {

    @Schema(description = "AI 建议分")
    private Integer suggestedScore;

    @Schema(description = "题目总分")
    private Integer totalScore;

    @Schema(description = "已命中的得分点")
    private List<String> matchedPoints;

    @Schema(description = "缺失的得分点")
    private List<String> missingPoints;

    @Schema(description = "答案中的错误表述")
    private List<String> incorrectStatements;

    @Schema(description = "改进建议")
    private String improvementSuggestion;

    @Schema(description = "评估置信度，取值范围为 0 到 1")
    private BigDecimal confidence;

    @Schema(description = "是否建议人工复核")
    private Boolean manualReviewRecommended;

    @Schema(description = "评分规则版本")
    private String rubricVersion;
}
