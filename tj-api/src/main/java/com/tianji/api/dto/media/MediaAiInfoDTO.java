package com.tianji.api.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 视频 AI 处理所需的媒资信息
 */
@Data
@Schema(description = "视频 AI 处理所需的媒资信息")
public class MediaAiInfoDTO {

    @Schema(description = "媒资 ID")
    private Long mediaId;

    @Schema(description = "云端文件 ID")
    private String fileId;

    @Schema(description = "文件名称")
    private String filename;

    @Schema(description = "可供转写服务访问的媒体地址")
    private String mediaUrl;

    @Schema(description = "视频时长，单位秒")
    private Float duration;

    @Schema(description = "媒资大小，单位字节")
    private Long size;

    @Schema(description = "媒资状态：1 上传中，2 已上传，3 已处理")
    private Integer status;

    @Schema(description = "媒资创建者 ID")
    private Long creater;
}