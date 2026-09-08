package com.tianji.exam.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 练习会话分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "练习会话分页查询参数")
public class PracticeSessionPageQuery extends PageQuery {

    @Schema(description = "课程 ID")
    private Long courseId;

    @Schema(description = "练习或测验目录 ID")
    private Long targetBizId;

    @Schema(description = "练习会话状态")
    private String status;
}
