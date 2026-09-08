package com.tianji.api.dto.aigc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


/**
 * 主观题 AI 评估请求参数
 */
@Data
@Schema(description = "主观题 AI 评估请求")
public class SubjectiveGradingRequestDTO {

    @Schema(description = "原始题目 ID")
    private Long questionId;

    @NotBlank(message = "题干不能为空")
    @Size(max = 1000, message = "题干不能超过 1000 个字符")
    @Schema(description = "题干")
    private String question;

    @NotBlank(message = "参考答案或评分要点不能为空")
    @Size(max = 10000, message = "参考答案或评分要点不能超过 10000 个字符")
    @Schema(description = "参考答案或评分要点")
    private String standardAnswer;

    @Schema(description = "题目解析")
    @Size(max = 300, message = "题目解析不能超过 300 个字符")
    private String analysis;

    @NotBlank(message = "学生答案不能为空")
    @Size(max = 10000, message = "学生答案不能超过 10000 个字符")
    @Schema(description = "学生答案")
    private String studentAnswer;

    @NotNull(message = "题目总分不能为空")
    @Min(value = 1, message = "题目总分必须大于 0")
    @Max(value = 100, message = "题目总分不能超过 100")
    @Schema(description = "题目总分")
    private Integer totalScore;

    @Schema(description = "评分规则版本")
    @Size(max = 128, message = "评分规则版本不能超过 128 个字符")
    private String rubricVersion;
}
