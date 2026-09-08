package com.tianji.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.auth.domain.po.LoginRecord;

/**
 * <p>
 * 登录信息记录表 服务类
 * </p>
 *
 * @author Sy
 * @since 2026-07-12
 */
public interface ILoginRecordService extends IService<LoginRecord> {

    void saveAsync(LoginRecord record);

    void loginSuccess(String cellphone, Long userId);
}
