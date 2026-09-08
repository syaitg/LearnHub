package com.tianji.exam.domain.vo;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主观题 AI 评估待审核项
 */
@Data
@Schema(description = "主观题 AI 评估待审核项")
public class PracticeAiReviewItemVO {

    @Schema(description = "练习答案 ID")
    private Long answerId;

    @Schema(description = "练习会话 ID")
    private Long sessionId;

    @Schema(description = "练习题快照 ID")
    private Long practiceQuestionId;

    @Schema(description = "正式题目 ID")
    private Long questionId;

    @Schema(description = "课程 ID")
    private Long courseId;

    @Schema(description = "练习目录 ID")
    private Long targetBizId;

    @Schema(description = "练习目录名称")
    private String targetName;

    @Schema(description = "学生用户 ID")
    private Long studentId;

    @Schema(description = "题目在练习中的顺序")
    private Integer sequenceNo;

    @Schema(description = "题干")
    private String questionName;

    @Schema(description = "题目类型")
    private Integer questionType;

    @Schema(description = "题目总分")
    private Integer questionScore;

    @Schema(description = "参考答案")
    private String standardAnswer;

    @Schema(description = "答案解析")
    private String analysis;

    @Schema(description = "学生答案")
    private String studentAnswer;

    @Schema(description = "答案状态")
    private String answerStatus;

    @Schema(description = "AI 建议分")
    private Integer aiSuggestedScore;

    @Schema(description = "AI 评估总分")
    private Integer aiTotalScore;

    @TableField(typeHandler = JacksonTypeHandler.class)
    @Schema(description = "已命中的得分点")
    private List<String> matchedPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    @Schema(description = "缺失的得分点")
    private List<String> missingPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    @Schema(description = "存在问题的表述")
    private List<String> incorrectStatements;

    @Schema(description = "改进建议")
    private String improvementSuggestion;

    @Schema(description = "AI 评估置信度")
    private BigDecimal confidence;

    @Schema(description = "是否建议人工复核")
    private Boolean manualReviewRecommended;

    @Schema(description = "评分规则版本")
    private String rubricVersion;

    @Schema(description = "AI 评估完成时间")
    private LocalDateTime evaluatedTime;
}
