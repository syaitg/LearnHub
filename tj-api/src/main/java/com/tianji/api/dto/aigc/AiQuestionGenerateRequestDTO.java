package com.tianji.api.dto.aigc;

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
 * AI 智能出题请求参数
 */
@Data
@Schema(description = "AI 智能出题请求")
public class AiQuestionGenerateRequestDTO {

    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 255, message = "课程名称不能超过 255 个字符")
    private String courseName;

    @NotBlank(message = "出题范围类型不能为空")
    @Size(max = 32, message = "出题范围类型不能超过 32 个字符")
    private String scopeType;

    @NotNull(message = "出题范围 ID 不能为空")
    private Long scopeId;

    @NotBlank(message = "出题范围名称不能为空")
    @Size(max = 255, message = "出题范围名称不能超过 255 个字符")
    private String scopeName;

    @Size(max = 50, message = "章级出题选择的小节不能超过 50 个")
    private List<@NotNull(message = "小节 ID 不能为空") Long> scopeSectionIds;

    @Size(max = 50, message = "章级出题选择的小节名称不能超过 50 个")
    private List<@NotBlank(message = "小节名称不能为空")
            @Size(max = 255, message = "小节名称不能超过 255 个字符") String> scopeSectionNames;

    @NotBlank(message = "素材来源类型不能为空")
    @Size(max = 32, message = "素材来源类型不能超过 32 个字符")
    private String sourceType;

    @Size(max = 128, message = "素材来源 ID 不能超过 128 个字符")
    private String sourceId;

    @Size(max = 128, message = "素材来源版本不能超过 128 个字符")
    private String sourceVersion;

    @Size(max = 30, message = "知识点不能超过 30 个")
    private List<@NotBlank(message = "知识点不能为空")
            @Size(max = 100, message = "单个知识点不能超过 100 个字符") String> knowledgePoints;

    @Size(max = 20000, message = "出题材料不能超过 20000 个字符")
    private String materialText;

    @NotEmpty(message = "题型不能为空")
    @Size(max = 5, message = "题型不能超过 5 种")
    private List<@NotNull(message = "题型不能为空")
            @Min(value = 1, message = "题型取值必须在 1 到 5 之间")
            @Max(value = 5, message = "题型取值必须在 1 到 5 之间") Integer> questionTypes;

    @NotNull(message = "题目数量不能为空")
    @Min(value = 1, message = "题目数量不能少于 1")
    @Max(value = 20, message = "题目数量不能超过 20")
    private Integer questionCount;

    @NotNull(message = "题目难度不能为空")
    @Min(value = 1, message = "题目难度取值必须在 1 到 3 之间")
    @Max(value = 3, message = "题目难度取值必须在 1 到 3 之间")
    private Integer difficulty;

    @NotNull(message = "题目分值不能为空")
    @Min(value = 1, message = "题目分值不能少于 1")
    @Max(value = 100, message = "题目分值不能超过 100")
    private Integer score;
}
