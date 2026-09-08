package com.tianji.gateway.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "tj.auth")
public class AuthProperties implements InitializingBean {

    private Set<String> excludePath = new HashSet<>();

    @Override
    public void afterPropertiesSet() {
        // 排除规则必须与 AccountAuthFilter 拼接的 METHOD:/path 格式保持一致。
        // 保留 Nacos 下发的规则，并补充认证流程必需的默认放行路径。
        excludePath.add("*:/error/**");
        excludePath.add("GET:/as/jwks");
        excludePath.add("POST:/as/accounts/login");
        excludePath.add("POST:/as/accounts/admin/login");
        excludePath.add("GET:/as/accounts/refresh");
        excludePath.add("OPTIONS:/**");
        // 课程封面通过图片标签加载，浏览器不会自动携带请求头，因此允许访问自有媒资文件内容。
        excludePath.add("GET:/ms/files/*/content");
    }
}
