package com.tianji.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.aigc.domain.dto.ChapterVideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.dto.VideoAiTaskCreateDTO;
import com.tianji.aigc.domain.dto.VideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.po.VideoAiTask;
import com.tianji.aigc.domain.query.VideoAiTaskPageQuery;
import com.tianji.aigc.domain.vo.VideoAiTaskVO;
import com.tianji.common.domain.dto.PageDTO;

/**
 * 视频 AI 处理任务服务
 */
public interface VideoAiTaskService extends IService<VideoAiTask> {

    /**
     * 创建视频 AI 处理任务
     *
     * @param dto 创建参数
     * @return 任务 ID
     */
    Long createTask(VideoAiTaskCreateDTO dto);

    /**
     * 分页查询当前用户的视频 AI 任务
     *
     * @param query 查询参数
     * @return 分页任务信息
     */
    PageDTO<VideoAiTaskVO> queryPage(VideoAiTaskPageQuery query);

    /**
     * 查询当前用户的视频 AI 任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    VideoAiTaskVO queryDetail(Long id);

    /**
     * 重试失败的视频 AI 任务
     *
     * @param id 任务 ID
     */
    void retry(Long id);

    /**
     * 复用 P1-A 创建视频来源的测验草稿批次
     *
     * @param id 视频 AI 任务 ID
     * @param dto 测验草稿参数
     * @return AI 出题批次 ID
     */
    Long createQuizDrafts(Long id, VideoQuizDraftCreateDTO dto);

    /**
     * 聚合多个视频分析结果并创建章级综合测试草稿批次
     *
     * @param dto 章级综合测试草稿参数
     * @return AI 出题批次 ID
     */
    Long createChapterQuizDrafts(ChapterVideoQuizDraftCreateDTO dto);
}