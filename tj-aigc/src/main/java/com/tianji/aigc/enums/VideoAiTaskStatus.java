package com.tianji.aigc.enums;

/**
 * 视频 AI 处理任务状态
 */
public enum VideoAiTaskStatus {
    CREATED,
    TRANSCRIBING,
    TRANSCRIBED,
    ANALYZING,
    COMPLETED,
    FAILED;

    /**
     * 判断当前状态是否已经结束
     *
     * @return 是否为结束状态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}