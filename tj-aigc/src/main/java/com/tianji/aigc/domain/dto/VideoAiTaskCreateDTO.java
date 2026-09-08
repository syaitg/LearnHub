package com.tianji.aigc.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建视频 AI 处理任务的参数
 */
@Data
@Schema(description = "创建视频 AI 处理任务的参数")
public class VideoAiTaskCreateDTO {

    @NotNull
    @Schema(description = "课程 ID")
    private Long courseId;

    @NotNull
    @Schema(description = "小节目录 ID")
    private Long sectionId;

    @NotNull
    @Schema(description = "媒资 ID")
    private Long mediaId;

    @Size(max = 64)
    @Schema(description = "客户端幂等键")
    private String requestId;
}