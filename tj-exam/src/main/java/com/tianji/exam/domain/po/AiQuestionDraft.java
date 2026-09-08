package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
/**
 * AI 题目草稿实体
 */
@Data
@Accessors(chain = true)
@TableName(value = "ai_question_draft", autoResultMap = true)
public class AiQuestionDraft {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long batchId;
    private Integer sequenceNo;
    private String name;
    private Integer type;
    private Integer difficulty;
    private Integer score;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;
    private String answer;
    private String analysis;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgePoints;
    private String contentFingerprint;
    private String status;
    private String validationMessage;
    private Long questionId;
    private Long confirmedBy;
    private LocalDateTime confirmedTime;
    private LocalDateTime publishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long creater;
    private Long updater;
}
