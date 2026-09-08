package com.tianji.aigc.task;

import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.event.VideoAiTaskRequestedEvent;
import com.tianji.aigc.domain.po.VideoAiTask;
import com.tianji.aigc.mapper.VideoAiTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频 AI 任务恢复与超时扫描器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoAiTaskTimeoutScanner {

    private final VideoAiTaskMapper taskMapper;
    private final VideoAiProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 定期重新投递长时间未开始执行的创建状态或待分析状态任务
     */
    @Scheduled(fixedDelayString = "${tj.ai.video.pending-scan-interval-ms:60000}")
    public void republishPendingTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getPendingRecoveryDelay());
        int limit = Math.max(1, properties.getRecoveryBatchSize());
        List<Long> taskIds = taskMapper.selectPendingTaskIds(cutoff, limit);
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        int publishedCount = 0;
        for (Long taskId : taskIds) {
            if (taskId == null || taskMapper.claimPendingTask(taskId, cutoff, LocalDateTime.now()) != 1) {
                continue;
            }
            eventPublisher.publishEvent(new VideoAiTaskRequestedEvent(taskId));
            publishedCount++;
        }
        if (publishedCount > 0) {
            log.info("本次共重新投递 {} 个待执行的视频 AI 任务", publishedCount);
        }
    }

    /**
     * 定期将长时间无进展的执行中任务标记为失败
     */
    @Scheduled(fixedDelayString = "${tj.ai.video.timeout-scan-interval-ms:60000}")
    public void markTimedOutTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(properties.getTaskTimeout());
        int limit = Math.max(1, properties.getRecoveryBatchSize());
        List<VideoAiTask> tasks = taskMapper.selectTimedOutTasks(cutoff, limit);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        int count = 0;
        for (VideoAiTask task : tasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            count += taskMapper.markTimedOut(task.getId(), retryCount, cutoff, now,
                    "视频 AI 任务执行超时，可稍后重试");
        }
        if (count > 0) {
            log.warn("本次共标记 {} 个超时的视频 AI 任务", count);
        }
    }
}
