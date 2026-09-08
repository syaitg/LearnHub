package com.tianji.exam.task;

import com.tianji.exam.config.ExamAiTaskProperties;
import com.tianji.exam.domain.event.AiQuestionGenerationRequestedEvent;
import com.tianji.exam.domain.event.PracticeSubjectiveReviewRequestedEvent;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import com.tianji.exam.service.PracticeAiReviewPersistenceService;
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
 * 考试 AI 异步任务恢复与超时扫描器测试
 */
@ExtendWith(MockitoExtension.class)
class ExamAiTaskRecoveryScannerTest {

    @Mock
    private AiQuestionBatchMapper batchMapper;
    @Mock
    private PracticeSessionMapper sessionMapper;
    @Mock
    private PracticeAnswerMapper answerMapper;
    @Mock
    private PracticeAiReviewPersistenceService reviewPersistenceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ExamAiTaskProperties properties;
    private ExamAiTaskRecoveryScanner scanner;

    /**
     * 初始化扫描器及其恢复配置。
     */
    @BeforeEach
    void setUp() {
        properties = new ExamAiTaskProperties();
        properties.setPendingRecoveryDelay(Duration.ofMinutes(2));
        properties.setGenerationTimeout(Duration.ofMinutes(30));
        properties.setReviewTimeout(Duration.ofMinutes(20));
        properties.setRecoveryBatchSize(25);
        scanner = new ExamAiTaskRecoveryScanner(batchMapper, sessionMapper, answerMapper,
                reviewPersistenceService, properties, eventPublisher);
    }

    /**
     * 验证过期的创建批次和已提交练习会话都会被重新投递。
     */
    @Test
    @DisplayName("待执行的考试 AI 任务应被重新投递")
    void shouldRepublishPendingGenerationAndReviewTasks() {
        when(batchMapper.selectStaleCreatedTasks(any(LocalDateTime.class), eq(25)))
                .thenReturn(List.of(new AiQuestionBatch().setId(11L).setRetryCount(0),
                        new AiQuestionBatch().setId(12L).setRetryCount(1)));
        when(batchMapper.claimStaleCreatedTask(any(Long.class), any(Integer.class),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(sessionMapper.selectStaleSubmittedSessions(any(LocalDateTime.class), eq(25)))
                .thenReturn(List.of(new PracticeSession().setId(21L).setUserId(31L).setAiReviewRetryCount(2)));
        when(sessionMapper.claimStaleSubmittedSession(any(Long.class), any(Integer.class),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);

        scanner.republishPendingTasks();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).containsExactly(
                new AiQuestionGenerationRequestedEvent(11L),
                new AiQuestionGenerationRequestedEvent(12L),
                new PracticeSubjectiveReviewRequestedEvent(21L, 31L, 2));
    }

    /**
     * 验证没有待恢复任务时不会发布无效事件。
     */
    @Test
    @DisplayName("没有待恢复任务时不应发布事件")
    void shouldNotPublishEventWhenNoPendingTaskExists() {
        when(batchMapper.selectStaleCreatedTasks(any(LocalDateTime.class), eq(25)))
                .thenReturn(Collections.emptyList());
        when(sessionMapper.selectStaleSubmittedSessions(any(LocalDateTime.class), eq(25)))
                .thenReturn(Collections.emptyList());

        scanner.republishPendingTasks();

        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 验证恢复扫描使用配置的延迟和最小批次大小。
     */
    @Test
    @DisplayName("恢复扫描应使用配置的延迟和最小批次大小")
    void shouldUseConfiguredDelayAndMinimumBatchSize() {
        properties.setRecoveryBatchSize(0);
        LocalDateTime before = LocalDateTime.now().minusMinutes(2);
        when(batchMapper.selectStaleCreatedTasks(any(LocalDateTime.class), eq(1)))
                .thenReturn(Collections.emptyList());
        when(sessionMapper.selectStaleSubmittedSessions(any(LocalDateTime.class), eq(1)))
                .thenReturn(Collections.emptyList());

        scanner.republishPendingTasks();

        LocalDateTime after = LocalDateTime.now().minusMinutes(2);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(batchMapper).selectStaleCreatedTasks(cutoffCaptor.capture(), eq(1));
        assertThat(cutoffCaptor.getValue()).isBetween(before, after);
    }

    /**
     * 验证超时的 AI 出题批次会被标记为生成失败。
     */
    @Test
    @DisplayName("超时的 AI 出题批次应被标记为失败")
    void shouldMarkTimedOutGenerationTasksAsFailed() {
        when(batchMapper.selectStaleProcessingTasks(any(LocalDateTime.class), eq(25)))
                .thenReturn(List.of(new AiQuestionBatch().setId(51L).setRetryCount(2)));
        when(batchMapper.markStaleProcessingFailed(any(Long.class), eq(2), any(LocalDateTime.class),
                eq("AI 出题任务执行超时，可稍后重试"), any(LocalDateTime.class))).thenReturn(1);

        scanner.markTimedOutGenerationTasks();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(batchMapper).markStaleProcessingFailed(eq(51L), eq(2), cutoffCaptor.capture(),
                eq("AI 出题任务执行超时，可稍后重试"), nowCaptor.capture());
        assertThat(Duration.between(cutoffCaptor.getValue(), nowCaptor.getValue()))
                .isEqualTo(Duration.ofMinutes(30));
    }

    /**
     * 验证超时扫描只委托持久化服务处理对应会话中的过期答案。
     */
    @Test
    @DisplayName("超时的主观题评估答案应被标记为失败")
    void shouldFailTimedOutSubjectiveReviews() {
        when(answerMapper.selectStaleReviewingSessionIds(any(LocalDateTime.class), eq(25)))
                .thenReturn(List.of(41L, 42L));
        when(reviewPersistenceService.failTimedOutReview(eq(41L), any(LocalDateTime.class),
                eq("主观题 AI 评估执行超时，可稍后重试"))).thenReturn(1);
        when(reviewPersistenceService.failTimedOutReview(eq(42L), any(LocalDateTime.class),
                eq("主观题 AI 评估执行超时，可稍后重试"))).thenReturn(2);

        scanner.markTimedOutReviewTasks();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(answerMapper).selectStaleReviewingSessionIds(cutoffCaptor.capture(), eq(25));
        verify(reviewPersistenceService).failTimedOutReview(
                41L, cutoffCaptor.getValue(), "主观题 AI 评估执行超时，可稍后重试");
        verify(reviewPersistenceService).failTimedOutReview(
                42L, cutoffCaptor.getValue(), "主观题 AI 评估执行超时，可稍后重试");
    }
}