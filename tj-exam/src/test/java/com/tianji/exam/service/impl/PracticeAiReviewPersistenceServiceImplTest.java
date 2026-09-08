package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.enums.PracticeAnswerStatus;
import com.tianji.exam.enums.PracticeSessionStatus;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主观题 AI 评估持久化服务测试
 */
@ExtendWith(MockitoExtension.class)
class PracticeAiReviewPersistenceServiceImplTest {

    /**
     * 初始化 MyBatis-Plus 实体元数据，便于单元测试构建 Lambda 条件。
     */
    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "practiceAnswer"), PracticeAnswer.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "practiceSession"), PracticeSession.class);
    }

    @Mock
    private PracticeAnswerMapper answerMapper;
    @Mock
    private PracticeSessionMapper sessionMapper;
    @InjectMocks
    private PracticeAiReviewPersistenceServiceImpl persistenceService;

    /**
     * 验证只返回本次从待评估状态成功领取的答案。
     */
    @Test
    @DisplayName("应只返回本次成功领取的答案")
    void shouldReturnOnlyAnswersClaimedByCurrentExecution() {
        PracticeAnswer firstPending = new PracticeAnswer().setId(1L);
        PracticeAnswer secondPending = new PracticeAnswer().setId(2L);
        PracticeAnswer firstClaimed = new PracticeAnswer().setId(1L).setPracticeQuestionId(11L);
        PracticeAnswer secondClaimed = new PracticeAnswer().setId(2L).setPracticeQuestionId(12L);
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(0));
        when(answerMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(firstPending, secondPending))
                .thenReturn(List.of(firstClaimed, secondClaimed));
        when(answerMapper.update(isNull(), any(Wrapper.class))).thenReturn(2);

        List<PracticeAnswer> claimed = persistenceService.claimPendingAnswers(100L, 0);

        assertThat(claimed).containsExactly(firstClaimed, secondClaimed);
        verify(sessionMapper).lockById(100L);
        verify(answerMapper).update(isNull(), any(Wrapper.class));
    }

    /**
     * 验证领取数量异常时中止任务，使事务回滚。
     */
    @Test
    @DisplayName("领取数量不一致时应中止任务")
    void shouldFailWhenClaimedCountDoesNotMatchPendingIds() {
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(0));
        when(answerMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(new PracticeAnswer().setId(1L), new PracticeAnswer().setId(2L)));
        when(answerMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertThatThrownBy(() -> persistenceService.claimPendingAnswers(100L, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("主观题 AI 评估任务领取数量不一致");
    }

    /**
     * 验证超时处理包含状态和截止时间条件，并在全部结束后完成会话。
     */
    @Test
    @DisplayName("超时处理应只更新截止时间前仍在评估中的答案")
    void shouldFailOnlyReviewingAnswersBeforeCutoff() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 18, 10, 0);
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(0));
        when(answerMapper.update(isNull(), any(Wrapper.class))).thenReturn(2);
        when(answerMapper.countUnfinishedAiReview(100L)).thenReturn(0);
        when(answerMapper.countAiReviewFailed(100L)).thenReturn(2);
        when(answerMapper.sumScoredAnswers(100L)).thenReturn(6);
        when(sessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int failedCount = persistenceService.failTimedOutReview(100L, cutoff, "评估超时");

        assertThat(failedCount).isEqualTo(2);
        ArgumentCaptor<Wrapper<PracticeAnswer>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(answerMapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("session_id", "status", "update_time");
        verify(sessionMapper).update(isNull(), any(Wrapper.class));
    }

    /**
     * 验证没有超时答案时不会修改练习会话状态。
     */
    @Test
    @DisplayName("没有超时答案时不应完成会话")
    void shouldNotCompleteSessionWhenNoAnswerTimesOut() {
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(0));
        when(answerMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        int failedCount = persistenceService.failTimedOutReview(
                100L, LocalDateTime.of(2026, 8, 18, 10, 0), "评估超时");

        assertThat(failedCount).isZero();
        verify(answerMapper, never()).countUnfinishedAiReview(any());
        verify(sessionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    /**
     * 验证仍有待处理答案时会刷新会话汇总，但不会将会话标记为已完成。
     */
    @Test
    @DisplayName("仍有待处理答案时应刷新会话汇总")
    void shouldRefreshSessionWhileAiReviewRemainsUnfinished() {
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(0));
        when(answerMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(answerMapper.countUnfinishedAiReview(100L)).thenReturn(1);
        when(answerMapper.countAiReviewFailed(100L)).thenReturn(1);
        when(answerMapper.sumScoredAnswers(100L)).thenReturn(6);
        when(sessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int failedCount = persistenceService.failTimedOutReview(
                100L, LocalDateTime.of(2026, 8, 18, 10, 0), "评估超时");

        assertThat(failedCount).isEqualTo(1);
        verify(sessionMapper).update(isNull(), any(Wrapper.class));
    }

    /**
     * 验证旧执行代际不能领取新代际的待评估答案。
     */
    @Test
    @DisplayName("旧执行代际不应领取新代际答案")
    void shouldIgnoreClaimWhenExecutionGenerationIsStale() {
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(2));

        List<PracticeAnswer> claimed = persistenceService.claimPendingAnswers(100L, 1);

        assertThat(claimed).isEmpty();
        verify(answerMapper, never()).selectList(any(Wrapper.class));
        verify(answerMapper, never()).update(isNull(), any(Wrapper.class));
    }

    /**
     * 验证旧执行代际返回成功结果时不能覆盖新代际数据。
     */
    @Test
    @DisplayName("旧执行代际不应保存评估结果")
    void shouldIgnoreCompletionWhenExecutionGenerationIsStale() {
        PracticeAnswer result = new PracticeAnswer().setId(1L).setAiSuggestedScore(8);
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(2));

        persistenceService.completeReview(100L, 1, List.of(result));

        verify(answerMapper, never()).selectById(any());
        verify(answerMapper, never()).updateById(any(PracticeAnswer.class));
        verify(answerMapper, never()).countUnfinishedAiReview(any());
        verify(sessionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    /**
     * 验证旧执行代际失败时不能污染新代际的答案和会话状态。
     */
    @Test
    @DisplayName("旧执行代际失败不应更新新代际状态")
    void shouldIgnoreFailureWhenExecutionGenerationIsStale() {
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(2));

        persistenceService.failReview(100L, 1, "旧任务失败");

        verify(answerMapper, never()).update(isNull(), any(Wrapper.class));
        verify(answerMapper, never()).countUnfinishedAiReview(any());
        verify(sessionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    /**
     * 验证当前执行代际仍可正常保存评估结果并完成会话。
     */
    @Test
    @DisplayName("当前执行代际应正常保存评估结果")
    void shouldCompleteReviewForCurrentExecutionGeneration() {
        PracticeAnswer result = new PracticeAnswer()
                .setId(1L)
                .setAiSuggestedScore(8)
                .setAiTotalScore(10)
                .setConfidence(new BigDecimal("0.90"));
        PracticeAnswer stored = new PracticeAnswer()
                .setId(1L)
                .setSessionId(100L)
                .setStatus(PracticeAnswerStatus.AI_REVIEWING.name());
        when(sessionMapper.lockById(100L)).thenReturn(100L);
        when(sessionMapper.selectById(100L)).thenReturn(reviewableSession(2));
        when(answerMapper.selectById(1L)).thenReturn(stored);
        when(answerMapper.updateById(stored)).thenReturn(1);
        when(answerMapper.countUnfinishedAiReview(100L)).thenReturn(0);
        when(answerMapper.countAiReviewed(100L)).thenReturn(1);
        when(answerMapper.sumScoredAnswers(100L)).thenReturn(6);
        when(sessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        persistenceService.completeReview(100L, 2, List.of(result));

        assertThat(stored.getStatus()).isEqualTo(PracticeAnswerStatus.AI_REVIEWED.name());
        assertThat(stored.getAiSuggestedScore()).isEqualTo(8);
        assertThat(stored.getAiTotalScore()).isEqualTo(10);
        assertThat(stored.getConfidence()).isEqualByComparingTo("0.90");
        verify(answerMapper).updateById(stored);
        verify(sessionMapper).update(isNull(), any(Wrapper.class));
    }
    /**
     * 创建处于当前主观题 AI 评估执行代际的练习会话
     *
     * @param retryCount 当前 AI 评估重试次数
     * @return 可执行主观题评估的练习会话
     */
    private PracticeSession reviewableSession(int retryCount) {
        return new PracticeSession()
                .setStatus(PracticeSessionStatus.SUBMITTED.name())
                .setAiReviewRetryCount(retryCount);
    }
}
