package com.tianji.api.client.aigc.fallback;

import com.tianji.api.client.aigc.AigcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * AIGC 文本问答降级处理
 */
@Slf4j
@Component
public class AigcClientFallback implements FallbackFactory<AigcClient> {

    /**
     * 创建 AIGC 文本问答降级实现
     *
     * @param cause 远程调用异常
     * @return 文本问答降级实现
     */
    @Override
    public AigcClient create(Throwable cause) {
        log.error("调用 AIGC 文本问答服务失败", cause);
        return question -> "AIGC 服务暂时不可用，请稍后重试";
    }
}
