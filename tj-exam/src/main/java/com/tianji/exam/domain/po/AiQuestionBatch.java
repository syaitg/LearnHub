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
 * AI 出题批次实体
 */
@Data
@Accessors(chain = true)
@TableName(value = "ai_question_batch", autoResultMap = true)
public class AiQuestionBatch {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long courseId;
    private String courseName;
    private String scopeType;
    private Long scopeId;
    private String scopeName;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> scopeSectionIds;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scopeSectionNames;
    private String sourceType;
    private String sourceId;
    private String sourceVersion;
    private Long targetBizId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgePoints;
    private String materialText;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> questionTypes;
    private Integer questionCount;
    private Integer difficulty;
    private Integer score;
    private Long cateId1;
    private Long cateId2;
    private Long cateId3;
    private String status;
    private Integer totalCount;
    private Integer validCount;
    private Integer duplicateCount;
    private Integer publishedCount;
    private String failureReason;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String requestId;
    private String requestFingerprint;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long creater;
    private Long updater;
}
