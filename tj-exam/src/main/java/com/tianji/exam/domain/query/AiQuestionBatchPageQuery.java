package com.tianji.exam.domain.query;

import com.tianji.common.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * AI 出题批次分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiQuestionBatchPageQuery extends PageQuery {
    private Long courseId;
    private Long scopeId;
    private Long targetBizId;
    private String scopeType;
    private String sourceType;
    private String status;
}
