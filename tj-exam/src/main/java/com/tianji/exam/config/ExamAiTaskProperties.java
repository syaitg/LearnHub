package com.tianji.exam.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 考试 AI 异步任务恢复配置
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "tj.ai.exam")
public class ExamAiTaskProperties {

    @NotNull
    private Duration pendingRecoveryDelay = Duration.ofMinutes(2);

    @NotNull
    private Duration generationTimeout = Duration.ofMinutes(30);

    @NotNull
    private Duration reviewTimeout = Duration.ofMinutes(30);

    @Min(1)
    private int recoveryBatchSize = 50;
}