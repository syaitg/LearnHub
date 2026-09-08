package com.tianji.exam.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 主观题 AI 评估待审核分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "主观题 AI 评估待审核分页查询参数")
public class PracticeAiReviewPageQuery extends PageQuery {

    @NotNull(message = "课程 ID 不能为空")
    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @Schema(description = "练习目录 ID")
    private Long targetBizId;

    @Schema(description = "学生用户 ID")
    private Long studentId;

    @Schema(description = "是否建议人工复核")
    private Boolean manualReviewRecommended;

    @DecimalMin(value = "0.0", message = "最低置信度不能小于 0")
    @DecimalMax(value = "1.0", message = "最低置信度不能大于 1")
    @Schema(description = "最低 AI 置信度，取值范围 0 到 1")
    private BigDecimal minConfidence;

    @DecimalMin(value = "0.0", message = "最高置信度不能小于 0")
    @DecimalMax(value = "1.0", message = "最高置信度不能大于 1")
    @Schema(description = "最高 AI 置信度，取值范围 0 到 1")
    private BigDecimal maxConfidence;
}
