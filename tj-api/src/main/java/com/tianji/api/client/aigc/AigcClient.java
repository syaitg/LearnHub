package com.tianji.api.client.aigc;

import com.tianji.api.client.aigc.fallback.AigcClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AIGC 文本问答远程调用接口
 */
@FeignClient(value = "aigc-service", contextId = "aigcChat", fallbackFactory = AigcClientFallback.class)
public interface AigcClient {

    /**
     * 获取大模型文本回复
     *
     * @param question 用户问题
     * @return 大模型文本回复
     */
    @PostMapping("/chat/text")
    String chatText(@RequestBody String question);
}
