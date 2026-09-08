package com.tianji.exam.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 练习题目详情
 */
@Data
@Schema(description = "练习题目详情")
public class PracticeQuestionVO {

    private Long id;
    private Long questionId;
    private Integer sequenceNo;
    private String name;
    private Integer type;
    private Integer difficulty;
    private Integer score;
    private List<String> options;

    @Schema(description = "提交练习后才返回的标准答案")
    private String standardAnswer;

    private String analysis;
    private String studentAnswer;
    private String answerStatus;
    private Integer actualScore;
    private Boolean correct;

    @Schema(description = "AI 建议分，不代表正式成绩")
    private Integer aiSuggestedScore;

    private List<String> matchedPoints;
    private List<String> missingPoints;
    private List<String> incorrectStatements;
    private String improvementSuggestion;
    private BigDecimal confidence;
    private Boolean manualReviewRecommended;
    private String rubricVersion;
    private String aiFailureReason;
}
