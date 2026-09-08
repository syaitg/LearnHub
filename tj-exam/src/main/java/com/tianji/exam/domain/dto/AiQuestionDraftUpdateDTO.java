package com.tianji.exam.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** AI 题目草稿编辑请求。 */
@Data
public class AiQuestionDraftUpdateDTO {
    @NotBlank(message = "题干不能为空")
    @Size(max = 1000, message = "题干不能超过 1000 个字符")
    private String name;
    @NotNull(message = "题型不能为空")
    @Min(value = 1, message = "题型必须在 1 到 5 之间")
    @Max(value = 5, message = "题型必须在 1 到 5 之间")
    private Integer type;
    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度必须在 1 到 3 之间")
    @Max(value = 3, message = "难度必须在 1 到 3 之间")
    private Integer difficulty;
    @NotNull(message = "分值不能为空")
    @Min(value = 1, message = "分值不能小于 1")
    @Max(value = 100, message = "分值不能超过 100")
    private Integer score;
    private List<String> options;
    @NotBlank(message = "答案不能为空")
    private String answer;
    @NotBlank(message = "答案解析不能为空")
    @Size(max = 300, message = "答案解析不能超过 300 个字符")
    private String analysis;
    @Size(max = 30, message = "知识点不能超过 30 个")
    private List<String> knowledgePoints;
}
