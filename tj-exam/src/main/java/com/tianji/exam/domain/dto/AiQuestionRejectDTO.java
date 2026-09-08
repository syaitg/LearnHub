package com.tianji.exam.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI 题目草稿驳回请求。 */
@Data
public class AiQuestionRejectDTO {
    @Size(max = 500)
    private String reason;
}
