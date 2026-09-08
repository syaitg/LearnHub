package com.tianji.aigc.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopePropertiesTest {

    @Test
    void shouldUseSafeHttpTimeoutDefaults() {
        var properties = new DashScopeProperties();

        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(120));
    }
}
