package com.tianji.aigc.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 视频分段摘要
 */
@Data
@Accessors(chain = true)
public class VideoSectionSummary {
    private String title;
    private String summary;
    private Long startMs;
    private Long endMs;
}