package com.tianji.auth.controller;


import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.auth.service.IAccountService;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.WebUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 账户登录相关接口
 */
@RestController
@RequestMapping("/accounts")
@Tag(name = "账户管理")
@RequiredArgsConstructor
public class AccountController {

    private final IAccountService accountService;

    @Operation(summary  = "登录并获取token")
    @PostMapping(value = "/login")
    public String loginByPw(@RequestBody LoginFormDTO loginFormDTO) {
        return accountService.login(loginFormDTO, false);
    }

    @Operation(summary  = "管理端登录并获取token")
    @PostMapping(value = "/admin/login")
    public String adminLoginByPw(@RequestBody LoginFormDTO loginFormDTO) {
        return accountService.login(loginFormDTO, true);
    }

    @Operation(summary  ="退出登录")
    @PostMapping(value = "/logout")
    public void logout(@RequestParam(value = "clientType", defaultValue = "student") String clientType) {
        accountService.logout("admin".equalsIgnoreCase(clientType));
    }

    @Operation(summary = "刷新token")
    @GetMapping(value = "/refresh")
    public String refreshToken(
            @CookieValue(value = JwtConstants.REFRESH_HEADER, required = false) String studentToken,
            @CookieValue(value = JwtConstants.ADMIN_REFRESH_HEADER, required = false) String adminToken,
            @RequestParam(value = "clientType", required = false) String clientType
    ) {
        // 前端显式声明端类型，避免同域路径部署、IP 访问或 HTTPS 场景下仅凭 Origin 误选 Cookie。
        String token = null;
        if ("student".equalsIgnoreCase(clientType)) {
            token = studentToken;
        } else if ("admin".equalsIgnoreCase(clientType)) {
            token = adminToken;
        } else if (studentToken != null && adminToken == null) {
            // 兼容旧客户端：只有一个刷新令牌时可直接确定其所属端。
            token = studentToken;
        } else if (adminToken != null && studentToken == null) {
            token = adminToken;
        } else {
            // 同时存在两类 Cookie 的旧客户端继续沿用原有域名约定。
            String origin = WebUtils.getHeader("origin");
            boolean studentOrigin = origin != null
                    && (origin.startsWith("http://www.") || origin.startsWith("https://www."));
            token = studentOrigin ? studentToken : adminToken;
        }
        if (token == null) {
            throw new BadRequestException("登录超时");
        }
        return accountService.refreshToken(WebUtils.cookieBuilder().decode(token));
    }
}
