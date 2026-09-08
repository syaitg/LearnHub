package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/**
 * 练习作答实体
 */
@Data
@Accessors(chain = true)
@TableName(value = "practice_answer", autoResultMap = true)
public class PracticeAnswer {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long sessionId;
    private Long practiceQuestionId;
    private Long questionId;
    private Long userId;
    private String studentAnswer;
    private String status;
    private Integer score;
    private Boolean correct;
    private Integer aiSuggestedScore;
    private Integer aiTotalScore;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> matchedPoints;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> missingPoints;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> incorrectStatements;
    private String improvementSuggestion;
    private BigDecimal confidence;
    private Boolean manualReviewRecommended;
    private String rubricVersion;
    private String aiFailureReason;
    private LocalDateTime answerTime;
    private LocalDateTime evaluatedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
