package com.tianji.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "当前用户修改密码请求")
public class CurrentUserPasswordDTO {

    @Schema(description = "原密码", example = "123456")
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码", example = "newPassword123")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度必须为6到32个字符")
    private String password;
}
