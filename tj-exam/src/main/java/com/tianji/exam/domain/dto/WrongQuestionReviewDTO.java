package com.tianji.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 错题重做提交参数
 */
@Data
@Schema(description = "错题重做提交参数")
public class WrongQuestionReviewDTO {

    @NotBlank(message = "重做答案不能为空")
    @Size(max = 10000, message = "重做答案不能超过 10000 个字符")
    @Schema(description = "学生本次重做答案")
    private String answer;
}
