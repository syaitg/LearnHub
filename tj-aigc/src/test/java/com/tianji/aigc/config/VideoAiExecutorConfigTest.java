package com.tianji.aigc.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class VideoAiExecutorConfigTest {

    @Test
    void shouldProvideSeparateDefaultAndVideoExecutors() {
        try (var context = new AnnotationConfigApplicationContext(VideoAiExecutorConfig.class)) {
            var defaultExecutor = context.getBean("taskExecutor", ThreadPoolTaskExecutor.class);
            var videoExecutor = context.getBean("videoAiTaskExecutor", ThreadPoolTaskExecutor.class);

            assertThat(defaultExecutor).isNotSameAs(videoExecutor);
            assertThat(defaultExecutor.getThreadNamePrefix()).isEqualTo("aigc-async-");
            assertThat(videoExecutor.getThreadNamePrefix()).isEqualTo("video-ai-");
        }
    }
}
