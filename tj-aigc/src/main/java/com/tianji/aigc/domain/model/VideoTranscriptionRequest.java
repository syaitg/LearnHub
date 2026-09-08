package com.tianji.aigc.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 视频转写 Provider 请求
 */
@Data
@Accessors(chain = true)
public class VideoTranscriptionRequest {
    private Long taskId;
    private Long mediaId;
    private String filename;
    private String mediaUrl;
    private Long size;
    private Long durationMs;
    private String language;
}