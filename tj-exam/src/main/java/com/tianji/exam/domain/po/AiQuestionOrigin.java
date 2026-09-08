package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
/**
 * AI 题目来源实体
 */
@Data
@Accessors(chain = true)
@TableName("ai_question_origin")
public class AiQuestionOrigin {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long questionId;
    private Long batchId;
    private Long draftId;
    private Long courseId;
    private String scopeType;
    private Long scopeId;
    private String sourceType;
    private String sourceId;
    private String sourceVersion;
    private Long targetBizId;
    private String contentFingerprint;
    private LocalDateTime createTime;
    private Long creater;
}
