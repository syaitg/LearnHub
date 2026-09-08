package com.tianji.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 练习答案保存参数
 */
@Data
@Schema(description = "练习答案保存参数")
public class PracticeAnswerSaveDTO {

    @NotNull(message = "答案不能为 null")
    @Size(max = 10000, message = "练习答案不能超过 10000 个字符")
    @Schema(description = "学生答案；允许空字符串表示暂时清空答案")
    private String answer;
}
