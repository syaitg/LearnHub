package com.tianji.aigc.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 文本与语音的互转服务
 */
public interface AudioService {

    /**
     * 文本转语音
     *
     * @param text 文本
     * @return 语音流
     */
    ResponseBodyEmitter ttsStream(String text);

    /**
     * 语音转文本
     *
     * @param multipartFile 语音文件
     * @return 文本内容
     */
    String stt(MultipartFile multipartFile);

    /**
     * 直接使用可公开访问的媒资地址进行语音转文本。
     *
     * <p>视频 AI 已经从媒资服务获取了带有效期的防盗链地址，优先使用该地址可以避免
     * 再上传一份临时文件到未配置的 OSS。普通语音上传场景仍使用 {@link #stt(MultipartFile)}。</p>
     *
     * @param mediaUrl 带访问授权的媒体地址
     * @return 转写文本
     */
    default String sttUrl(String mediaUrl) {
        throw new UnsupportedOperationException("当前语音服务不支持通过媒资地址转写");
    }

}
