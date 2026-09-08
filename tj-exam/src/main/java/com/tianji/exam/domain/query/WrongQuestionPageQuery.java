package com.tianji.exam.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 错题分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "错题分页查询参数")
public class WrongQuestionPageQuery extends PageQuery {

    @Schema(description = "课程 ID")
    private Long courseId;

    @Schema(description = "错题掌握状态")
    private String status;
}
