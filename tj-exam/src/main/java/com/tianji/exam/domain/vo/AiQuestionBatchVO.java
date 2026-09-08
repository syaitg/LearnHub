package com.tianji.exam.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
/**
 * AI 出题批次视图数据
 */
@Data
public class AiQuestionBatchVO {
    private Long id;
    private Long courseId;
    private String courseName;
    private String scopeType;
    private Long scopeId;
    private String scopeName;
    private List<Long> scopeSectionIds;
    private List<String> scopeSectionNames;
    private String sourceType;
    private String sourceId;
    private String sourceVersion;
    private Long targetBizId;
    private List<String> knowledgePoints;
    private List<Integer> questionTypes;
    private Integer questionCount;
    private Integer difficulty;
    private Integer score;
    private String status;
    private Integer totalCount;
    private Integer validCount;
    private Integer duplicateCount;
    private Integer publishedCount;
    private String failureReason;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String requestId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long creater;
    private List<AiQuestionDraftVO> drafts;
}
