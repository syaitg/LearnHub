package com.tianji.exam.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 练习会话详情
 */
@Data
@Schema(description = "练习会话详情")
public class PracticeSessionVO {

    private Long id;
    private Long courseId;
    private Long targetBizId;
    private String targetName;
    private String status;
    private Integer totalQuestions;
    private Integer answeredCount;
    private Integer objectiveCount;
    private Integer subjectiveCount;
    private Integer totalScore;
    private Integer objectiveFullScore;
    private Integer objectiveScore;
    private Integer finalScore;
    private Integer correctCount;
    private Integer aiReviewPendingCount;

    @Schema(description = "主观题 AI 评估已重试次数")
    private Integer aiReviewRetryCount;

    @Schema(description = "主观题 AI 评估最大重试次数")
    private Integer aiReviewMaxRetryCount;

    @Schema(description = "客观题正确率")
    private Double objectiveAccuracy;

    @Schema(description = "主观题 AI 评估免责声明")
    private String aiDisclaimer;

    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private List<PracticeQuestionVO> questions;
}
