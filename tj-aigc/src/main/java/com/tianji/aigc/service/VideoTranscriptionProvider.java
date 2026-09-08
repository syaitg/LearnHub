package com.tianji.aigc.service;

import com.tianji.aigc.domain.model.VideoTranscriptionRequest;
import com.tianji.aigc.domain.model.VideoTranscriptionResult;

/**
 * 视频转写 Provider 抽象
 */
public interface VideoTranscriptionProvider {

    /**
     * 获取 Provider 标识
     *
     * @return Provider 标识
     */
    String providerName();

    /**
     * 将视频或音频媒资转写为文本及时间戳句段
     *
     * @param request 转写请求
     * @return 统一转写结果
     */
    VideoTranscriptionResult transcribe(VideoTranscriptionRequest request);
}