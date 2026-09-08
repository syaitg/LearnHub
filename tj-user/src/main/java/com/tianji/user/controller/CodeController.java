package com.tianji.user.controller;

import com.tianji.user.service.ICodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/code")
@Tag(name = "验证码接口")
@RequiredArgsConstructor
@Validated
public class CodeController {

    private final ICodeService codeService;

    @Operation(summary = "发送短信验证码")
    @PostMapping("/verifycode")
    public void sendVerifyCode(
            @RequestParam("cellPhone")
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误") String cellPhone) {
        codeService.sendVerifyCode(cellPhone);
    }
}
