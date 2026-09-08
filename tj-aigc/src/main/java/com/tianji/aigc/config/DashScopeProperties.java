package com.tianji.aigc.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.dashscope")
public class DashScopeProperties {

    private String key;
    /** AI HTTP connection timeout. */
    private Duration connectTimeout = Duration.ofSeconds(10);
    /** AI synchronous response read timeout. */
    private Duration readTimeout = Duration.ofSeconds(120);
    private AppAgent appAgent;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AppAgent {
        private String id;
        private List<String> tools;
    }

}
