package com.tianji.common.utils;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
@Data
@Accessors(chain = true, fluent = true)
public class CookieBuilder {
    private Charset charset = StandardCharsets.UTF_8;
    private int maxAge = -1;
    private String path = "/";
    private boolean httpOnly;
    private String name;
    private String value;
    private String domain;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public CookieBuilder(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    /**
     * 构建cookie，会对cookie值用UTF-8做URL编码，避免中文乱码
     */
    public void build(){
        if (response == null) {
            log.error("response为null，无法写入cookie");
            return;
        }
        Cookie cookie = new Cookie(name, URLEncoder.encode(value, charset));
        if (StringUtils.isNotBlank(domain)) {
            cookie.setDomain(domain);
        } else if (request != null) {
            String serverName = request.getServerName();
            // IP 地址、IPv6 地址和 localhost 不设置 Domain，让浏览器按当前主机保存 Cookie。
            // 否则 IP 访问时截取出的伪域名会导致刷新令牌 Cookie 被浏览器拒收。
            if (isDomainName(serverName)) {
                String[] labels = serverName.split("\\.");
                // 仅多级域名需要共享到根域名；二级域名直接使用当前主机名。
                cookie.setDomain(labels.length > 2
                        ? StringUtils.subAfter(serverName, ".", false)
                        : serverName);
            }
        }
        cookie.setHttpOnly(httpOnly);
        cookie.setMaxAge(maxAge);
        cookie.setPath(path);
        log.debug("生成cookie，编码方式:{}，【{}={}，domain:{};maxAge={};path={};httpOnly={}】",
                charset.name(), name, value, cookie.getDomain(), maxAge, path, httpOnly);
        response.addCookie(cookie);
    }

    /**
     * 判断当前主机名是否适合设置 Domain 属性。
     */
    private boolean isDomainName(String serverName) {
        if (StringUtils.isBlank(serverName)
                || "localhost".equalsIgnoreCase(serverName)
                || serverName.contains(":")
                || serverName.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            return false;
        }
        return serverName.contains(".");
    }

    /**
     * 利用UTF-8对cookie值解码，避免中文乱码
     * @param cookieValue cookie原始值
     * @return 解码后的值
     */
    public String decode(String cookieValue){
        return URLDecoder.decode(cookieValue, charset);
    }
}
