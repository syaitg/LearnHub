package com.tianji.aigc.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 视频转写句段及时间范围
 */
@Data
@Accessors(chain = true)
public class TranscriptSegment {
    private Long startMs;
    private Long endMs;
    private String text;
}