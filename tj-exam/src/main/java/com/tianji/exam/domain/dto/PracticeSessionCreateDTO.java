package com.tianji.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 练习会话创建参数
 */
@Data
@Schema(description = "练习会话创建参数")
public class PracticeSessionCreateDTO {

    @NotNull(message = "课程 ID 不能为空")
    @Schema(description = "课程 ID")
    private Long courseId;

    @NotNull(message = "练习目录 ID 不能为空")
    @Schema(description = "练习或测验目录 ID")
    private Long targetBizId;

    @Size(max = 64, message = "幂等请求标识不能超过 64 个字符")
    @Schema(description = "客户端幂等请求标识")
    private String requestId;
}
