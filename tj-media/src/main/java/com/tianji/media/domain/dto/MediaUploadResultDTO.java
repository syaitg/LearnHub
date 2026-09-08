package com.tianji.media.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.tianji.media.enums.MediaSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "媒体上传的结果")
public class MediaUploadResultDTO {

    @Schema(description = "文件在云端的唯一标示", example = "387702302659783576")
    private String fileId;

    @Schema(description = "上传使用的账号来源；新上传视频填写OWN_TENCENT，历史官方视频填写OFFICIAL_TENCENT")
    private MediaSource source;

    @Schema(description = "视频名称，查询云端信息失败时作为兜底")
    private String filename;

    @Schema(description = "视频地址，查询云端信息失败时作为兜底")
    private String mediaUrl;

    @Schema(description = "视频封面地址，查询云端信息失败时作为兜底")
    private String coverUrl;

    @Schema(description = "视频大小，查询云端信息失败时作为兜底")
    private Long size;

    @Schema(description = "视频时长，查询云端信息失败时作为兜底")
    private Float duration;

}
