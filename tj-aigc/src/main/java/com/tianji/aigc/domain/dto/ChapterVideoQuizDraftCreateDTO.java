package com.tianji.aigc.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 根据多个视频分析结果创建章级综合测试草稿的参数
 */
@Data
@Schema(description = "根据多个视频分析结果创建章级综合测试草稿的参数")
public class ChapterVideoQuizDraftCreateDTO {

    @NotNull
    @Schema(description = "课程 ID")
    private Long courseId;

    @NotNull
    @Schema(description = "章节目录 ID")
    private Long chapterId;

    @NotEmpty
    @Size(max = 20)
    @Schema(description = "用于聚合出题的视频 AI 任务 ID 列表")
    private List<@NotNull Long> videoTaskIds;

    @NotNull
    @Schema(description = "章级综合测试目录 ID")
    private Long targetBizId;

    @NotEmpty
    @Schema(description = "题型列表")
    private List<Integer> questionTypes;

    @NotNull
    @Min(1)
    @Max(20)
    @Schema(description = "题目数量")
    private Integer questionCount;

    @NotNull
    @Min(1)
    @Max(3)
    @Schema(description = "难度：1 简单，2 中等，3 困难")
    private Integer difficulty;

    @NotNull
    @Min(1)
    @Max(100)
    @Schema(description = "每道题分值")
    private Integer score;

    @Size(max = 64)
    @Schema(description = "客户端幂等键")
    private String requestId;
}
