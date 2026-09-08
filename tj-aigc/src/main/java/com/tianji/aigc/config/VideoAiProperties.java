package com.tianji.aigc.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频 AI 处理配置
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "tj.ai.video")
public class VideoAiProperties {

    @NotBlank
    @Size(max = 64)
    private String provider = "audio-service";

    @NotBlank
    @Size(max = 64)
    private String providerVersion = "v1";

    /**
     * 配置版本最多保留四十八个字符，为结果版本的时间后缀预留数据库字段空间。
     */
    @NotBlank
    @Size(max = 48)
    private String configVersion = "video-ai-v1";

    @NotBlank
    @Size(max = 16)
    private String language = "zh";

    @Min(1)
    private long maxSizeBytes = 536870912L;

    @Min(1)
    private long maxDurationSeconds = 14400L;

    @Min(0)
    private int maxRetryCount = 3;

    @Min(1)
    private int analysisMaxChars = 60000;

    @Min(1)
    private int materialMaxChars = 20000;

    /**
     * 单个视频转写全文的最大字符数。
     */
    @Min(1)
    private int transcriptionMaxChars = 500000;

    /**
     * 单个视频转写结果允许保存的最大句段数。
     */
    @Min(1)
    private int transcriptMaxSegments = 20000;

    /**
     * 单个转写句段允许保存的最大字符数。
     */
    @Min(1)
    private int transcriptSegmentMaxChars = 5000;

    @NotNull
    private Duration requestTimeout = Duration.ofMinutes(20);

    @NotNull
    private Duration taskTimeout = Duration.ofMinutes(30);

    @NotNull
    private Duration pendingRecoveryDelay = Duration.ofMinutes(2);

    @Min(1)
    private int recoveryBatchSize = 50;

    @NotEmpty
    private List<@NotBlank @Size(max = 16) String> acceptedExtensions = new ArrayList<>(List.of(
            "mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm"
    ));
}
