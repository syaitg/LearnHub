package com.tianji.user.domain.dto;

import com.tianji.common.validate.annotations.EnumValid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "当前用户资料修改请求")
public class CurrentUserProfileDTO {

    @Schema(description = "姓名", example = "张三")
    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名长度不能超过32个字符")
    private String name;

    @Schema(description = "性别：0-男，1-女", example = "0")
    @NotNull(message = "性别不能为空")
    @EnumValid(enumeration = {0, 1}, message = "性别只能为0或1")
    private Integer gender;

    @Schema(description = "头像地址", example = "default-user-icon.jpg")
    @Size(max = 255, message = "头像地址长度不能超过255个字符")
    private String icon;

    @Schema(description = "岗位", example = "Java工程师")
    @Size(max = 20, message = "岗位长度不能超过20个字符")
    private String job;

    @Schema(description = "个人介绍", example = "专注Java后端开发")
    @Size(max = 200, message = "个人介绍长度不能超过200个字符")
    private String intro;

    @Schema(description = "教师形象照地址")
    @Size(max = 255, message = "教师形象照地址长度不能超过255个字符")
    private String photo;
}
