package com.tianji.aigc.service;

import com.tianji.aigc.domain.model.VideoContentAnalysisResult;
import com.tianji.aigc.domain.po.VideoAiTask;

/**
 * 视频摘要与知识点分析服务
 */
public interface VideoContentAnalysisService {

    /**
     * 根据视频转写结果生成摘要和知识点
     *
     * @param task 已完成转写的任务
     * @return 视频内容分析结果
     */
    VideoContentAnalysisResult analyze(VideoAiTask task);
}