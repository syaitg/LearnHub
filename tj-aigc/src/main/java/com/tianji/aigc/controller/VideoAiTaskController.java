package com.tianji.aigc.controller;

import com.tianji.aigc.domain.dto.ChapterVideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.dto.VideoAiTaskCreateDTO;
import com.tianji.aigc.domain.dto.VideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.query.VideoAiTaskPageQuery;
import com.tianji.aigc.domain.vo.VideoAiTaskVO;
import com.tianji.aigc.service.VideoAiTaskService;
import com.tianji.common.domain.dto.PageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频转写、摘要、知识点与测验草稿接口
 */
@Tag(name = "视频 AI 处理")
@RestController
@RequestMapping("/video-ai-tasks")
@RequiredArgsConstructor
public class VideoAiTaskController {

    private final VideoAiTaskService taskService;

    /**
     * 创建视频 AI 处理任务
     *
     * @param dto 创建参数
     * @return 任务 ID
     */
    @Operation(summary = "创建视频转写与内容分析任务")
    @PostMapping
    public Long createTask(@Valid @RequestBody VideoAiTaskCreateDTO dto) {
        return taskService.createTask(dto);
    }

    /**
     * 分页查询当前用户的视频 AI 任务
     *
     * @param query 查询参数
     * @return 分页任务信息
     */
    @Operation(summary = "分页查询视频 AI 任务")
    @GetMapping("/page")
    public PageDTO<VideoAiTaskVO> queryPage(VideoAiTaskPageQuery query) {
        return taskService.queryPage(query);
    }

    /**
     * 查询视频 AI 任务详情及处理结果
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @Operation(summary = "查询视频 AI 任务详情")
    @GetMapping("/{id}")
    public VideoAiTaskVO queryDetail(@PathVariable("id") Long id) {
        return taskService.queryDetail(id);
    }

    /**
     * 重试失败的视频 AI 任务
     *
     * @param id 任务 ID
     */
    @Operation(summary = "重试失败的视频 AI 任务")
    @PostMapping("/{id}/retry")
    public void retry(@PathVariable("id") Long id) {
        taskService.retry(id);
    }

    /**
     * 根据视频分析结果创建 P1-A 测验草稿批次
     *
     * @param id 视频 AI 任务 ID
     * @param dto 测验草稿参数
     * @return AI 出题批次 ID
     */
    @Operation(summary = "根据视频分析结果创建测验草稿")
    @PostMapping("/{id}/quiz-drafts")
    public Long createQuizDrafts(
            @PathVariable("id") Long id,
            @Valid @RequestBody VideoQuizDraftCreateDTO dto) {
        return taskService.createQuizDrafts(id, dto);
    }

    /**
     * 聚合多个视频分析结果并创建章级综合测试草稿批次
     *
     * @param dto 章级综合测试草稿参数
     * @return AI 出题批次 ID
     */
    @Operation(summary = "根据多个视频分析结果创建章级综合测试草稿")
    @PostMapping("/chapter-quiz-drafts")
    public Long createChapterQuizDrafts(@Valid @RequestBody ChapterVideoQuizDraftCreateDTO dto) {
        return taskService.createChapterQuizDrafts(dto);
    }
}