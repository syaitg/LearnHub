package com.tianji.aigc.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 视频 AI 任务执行请求事件
 */
@Getter
@RequiredArgsConstructor
public class VideoAiTaskRequestedEvent {
    private final Long taskId;
}