package com.tianji.aigc.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 视频转写 Provider 统一结果
 */
@Data
@Accessors(chain = true)
public class VideoTranscriptionResult {
    private String language;
    private String fullText;
    private List<TranscriptSegment> segments;
    private String provider;
    private String providerVersion;
    private String timestampMode;
}