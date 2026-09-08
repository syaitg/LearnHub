package com.tianji.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 出题批次创建参数
 */
@Data
@Schema(description = "创建 AI 出题草稿批次")
public class AiQuestionBatchCreateDTO {
    @NotNull(message = "课程不能为空")
    private Long courseId;
    @NotBlank(message = "出题范围类型不能为空")
    private String scopeType;
    @NotNull(message = "出题范围不能为空")
    private Long scopeId;
    @Size(max = 50)
    private List<@NotNull(message = "小节 ID 不能为空") Long> scopeSectionIds;
    @NotBlank(message = "来源类型不能为空")
    private String sourceType;
    @Size(max = 128)
    private String sourceId;
    @Size(max = 128)
    private String sourceVersion;
    @NotNull(message = "发布目录不能为空")
    private Long targetBizId;
    @Size(max = 30, message = "知识点不能超过 30 个")
    private List<@Size(max = 100, message = "单个知识点不能超过 100 个字符") String> knowledgePoints;
    @Size(max = 20000, message = "出题材料不能超过 20000 个字符")
    private String materialText;
    @NotEmpty(message = "题型不能为空")
    @Size(max = 5, message = "题型不能超过 5 种")
    private List<@NotNull(message = "题型不能为空") @Min(value = 1, message = "题型取值必须在 1 到 5 之间") @Max(value = 5, message = "题型取值必须在 1 到 5 之间") Integer> questionTypes;
    @NotNull(message = "题目数量不能为空")
    @Min(value = 1, message = "题目数量不能小于 1")
    @Max(value = 20, message = "题目数量不能超过 20")
    private Integer questionCount;
    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度必须在 1 到 3 之间")
    @Max(value = 3, message = "难度必须在 1 到 3 之间")
    private Integer difficulty;
    @NotNull(message = "每题分值不能为空")
    @Min(value = 1, message = "每题分值不能小于 1")
    @Max(value = 100, message = "每题分值不能超过 100")
    private Integer score;
    @Size(max = 64, message = "请求 ID 不能超过 64 个字符")
    private String requestId;
}
