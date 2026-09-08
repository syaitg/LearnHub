package com.tianji.media.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "视频播放的签名信息")
public class VideoPlayVO {
    @Schema(description = "视频唯一标示", example = "12412534535143242")
    private String fileId;
    @Schema(description = "视频封面", example = "xxx.xxx.xxx")
    private String signature;
    @Schema(description = "视频所属腾讯云账号 APPID", example = "1312394356")
    private Long appId;
    @Schema(description = "带 Key 防盗链参数的播放地址；自有 VOD 使用该字段，psign 播放时为空",
            example = "https://example.vod-qcloud.com/video.mp4?t=68b8a000&sign=xxx")
    private String playUrl;
}
