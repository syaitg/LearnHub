package com.tianji.aigc.task;

import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.event.VideoAiTaskRequestedEvent;
import com.tianji.aigc.domain.po.VideoAiTask;
import com.tianji.aigc.mapper.VideoAiTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频 AI 任务恢复与超时扫描器测试
 */
@ExtendWith(MockitoExtension.class)
class VideoAiTaskTimeoutScannerTest {

    @Mock
    private VideoAiTaskMapper taskMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private VideoAiProperties properties;
    private VideoAiTaskTimeoutScanner scanner;

    /**
     * 初始化任务扫描器及其配置
     */
    @BeforeEach
    void setUp() {
        properties = new VideoAiProperties();
        properties.setPendingRecoveryDelay(Duration.ofMinutes(2));
        properties.setRecoveryBatchSize(50);
        properties.setTaskTimeout(Duration.ofMinutes(30));
        scanner = new VideoAiTaskTimeoutScanner(taskMapper, properties, eventPublisher);
    }

    /**
     * 验证没有待恢复任务时不会发布执行事件
     */
    @Test
    @DisplayName("没有待恢复任务时不应发布事件")
    void shouldNotPublishEventWhenNoPendingTaskExists() {
        when(taskMapper.selectPendingTaskIds(any(LocalDateTime.class), eq(50)))
                .thenReturn(Collections.emptyList());

        scanner.republishPendingTasks();

        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 验证创建状态和待分析状态任务能够被重新投递
     */
    @Test
    @DisplayName("待恢复任务应被重新投递")
    void shouldRepublishPendingTasks() {
        when(taskMapper.selectPendingTaskIds(any(LocalDateTime.class), eq(50)))
                .thenReturn(List.of(11L, 12L));
        when(taskMapper.claimPendingTask(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        scanner.republishPendingTasks();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event).isInstanceOf(VideoAiTaskRequestedEvent.class));
        assertThat(eventCaptor.getAllValues())
                .extracting(event -> ((VideoAiTaskRequestedEvent) event).getTaskId())
                .containsExactly(11L, 12L);
    }

    /**
     * 验证恢复批次配置小于一时仍按最小批次查询
     */
    @Test
    @DisplayName("恢复批次应至少为一")
    void shouldUseMinimumRecoveryBatchSize() {
        properties.setRecoveryBatchSize(0);
        when(taskMapper.selectPendingTaskIds(any(LocalDateTime.class), eq(1)))
                .thenReturn(Collections.emptyList());

        scanner.republishPendingTasks();

        verify(taskMapper).selectPendingTaskIds(any(LocalDateTime.class), eq(1));
    }

    /**
     * 验证执行超时任务会被批量标记为失败
     */
    @Test
    @DisplayName("执行超时任务应被标记为失败")
    void shouldMarkTimedOutTasksAsFailed() {
        when(taskMapper.selectTimedOutTasks(any(LocalDateTime.class), eq(50)))
                .thenReturn(List.of(new VideoAiTask().setId(11L).setRetryCount(2)));
        when(taskMapper.markTimedOut(any(Long.class), eq(2), any(LocalDateTime.class),
                any(LocalDateTime.class), any(String.class))).thenReturn(1);

        scanner.markTimedOutTasks();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskMapper).markTimedOut(eq(11L), eq(2), cutoffCaptor.capture(), nowCaptor.capture(),
                any(String.class));
        assertThat(Duration.between(cutoffCaptor.getValue(), nowCaptor.getValue()))
                .isEqualTo(Duration.ofMinutes(30));
    }
}
