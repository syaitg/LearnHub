package com.tianji.user.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户详情")
public class UserDetailVO {
    @Schema(description = "用户id", example = "1")
    private Long id;

    @Schema(description = "名字", example = "张三")
    private String name;

    @Schema(description = "头像", example = "default-icon.jpg")
    private String icon;

    @Schema(description = "用户类型：1-员工，2-学生，3-教师", example = "3")
    private Integer type;

    @Schema(description = "手机号", example = "13800010004")
    private String cellPhone;

    @Schema(description = "用户名", example = "13800010004")
    private String username;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "QQ号码")
    private String qq;

    @Schema(description = "个人介绍")
    private String intro;

    @Schema(description = "教师岗位")
    private String job;

    @Schema(description = "教师形象照")
    private String photo;

    @Schema(description = "省")
    private String province;

    @Schema(description = "市")
    private String city;

    @Schema(description = "区")
    private String district;

    @Schema(description = "性别：0-男性，1-女性", example = "0")
    private Integer gender;

    @Schema(description = "注册时间", example = "2026-07-12")
    private LocalDateTime createTime;

    @Schema(description = "角色名称", example = "教师")
    private String roleName;
}
