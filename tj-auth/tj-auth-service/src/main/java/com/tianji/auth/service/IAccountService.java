package com.tianji.auth.service;

import com.tianji.api.dto.user.LoginFormDTO;

/**
 * <p>
 * 账号表，平台内所有用户的账号、密码信息 服务类
 * </p>
 *
 * @author Sy
 * @since 2026-06-16
 */
public interface IAccountService{

    String login(LoginFormDTO loginFormDTO, boolean isStaff);

    void logout(boolean isAdmin);

    String refreshToken(String refreshToken);
}
