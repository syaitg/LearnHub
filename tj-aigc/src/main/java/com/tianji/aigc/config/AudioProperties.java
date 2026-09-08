package com.tianji.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 音频服务配置。
 *
 * <p>该配置只负责选择音频实现和语音转写模型，和聊天、Agent 使用的文本模型配置分开。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.audio")
public class AudioProperties {

    /** 音频服务实现类型：OPENAI 或 DASHSCOPE。 */
    private String type = "DASHSCOPE";

    /** 语音转写专用模型，不影响普通聊天模型。 */
    private String transcriptionModel = "paraformer-v2";
}
