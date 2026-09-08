package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tianji.api.client.aigc.AigcTaskClient;
import com.tianji.api.dto.aigc.SubjectiveGradingRequestDTO;
import com.tianji.api.dto.aigc.SubjectiveGradingResultDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.po.PracticeQuestion;
import com.tianji.exam.mapper.PracticeQuestionMapper;
import com.tianji.exam.service.PracticeAiReviewPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主观题 AI 异步评估任务服务测试
 */
@ExtendWith(MockitoExtension.class)
class PracticeAiReviewTaskServiceImplTest {

    @Mock
    private PracticeAiReviewPersistenceService persistenceService;
    @Mock
    private PracticeQuestionMapper questionMapper;
    @Mock
    private AigcTaskClient aigcTaskClient;
    @InjectMocks
    private PracticeAiReviewTaskServiceImpl taskService;

    /**
     * 每个测试结束后清理线程中的用户上下文
     */
    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    /**
     * 验证没有待评估答案时不会调用大模型
     */
    @Test
    @DisplayName("没有待评估答案时应直接结束")
    void shouldSkipWhenNoAnswerWasClaimed() {
        when(persistenceService.claimPendingAnswers(1L, 0)).thenReturn(List.of());

        taskService.reviewAsync(1L, 10L, 0);

        verify(aigcTaskClient, never()).gradeSubjective(any());
        verify(persistenceService, never()).completeReview(any(), anyInt(), any());
        verify(persistenceService, never()).failReview(any(), anyInt(), any());
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 验证成功调用大模型后会保存结构化评估结果
     */
    @Test
    @DisplayName("AI 评估成功后应保存建议结果")
    void shouldCompleteReviewWithAiResult() {
        PracticeAnswer answer = pendingAnswer();
        PracticeQuestion question = subjectiveQuestion();
        SubjectiveGradingResultDTO result = gradingResult();
        when(persistenceService.claimPendingAnswers(1L, 0)).thenReturn(List.of(answer));
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question));
        when(aigcTaskClient.gradeSubjective(any())).thenReturn(result);

        taskService.reviewAsync(1L, 10L, 0);

        ArgumentCaptor<SubjectiveGradingRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(SubjectiveGradingRequestDTO.class);
        verify(aigcTaskClient).gradeSubjective(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStudentAnswer()).isEqualTo("学生作答");
        assertThat(requestCaptor.getValue().getRubricVersion()).isEqualTo("subjective-rubric-v1");
        verify(persistenceService).completeReview(eq(1L), eq(0), eq(List.of(answer)));
        assertThat(answer.getAiSuggestedScore()).isEqualTo(8);
        assertThat(answer.getAiTotalScore()).isEqualTo(10);
        assertThat(answer.getMatchedPoints()).containsExactly("得分点一");
        assertThat(answer.getMissingPoints()).isEmpty();
        assertThat(answer.getIncorrectStatements()).isEmpty();
        assertThat(answer.getConfidence()).isEqualByComparingTo("0.85");
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 验证大模型调用异常时会将已经领取的答案标记为失败
     */
    @Test
    @DisplayName("AI 调用异常时应记录评估失败")
    void shouldFailClaimedAnswersWhenAiCallThrowsException() {
        PracticeAnswer answer = pendingAnswer();
        when(persistenceService.claimPendingAnswers(1L, 0)).thenReturn(List.of(answer));
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(subjectiveQuestion()));
        when(aigcTaskClient.gradeSubjective(any())).thenThrow(new RuntimeException("AIGC 服务不可用"));

        taskService.reviewAsync(1L, 10L, 0);

        verify(persistenceService).failReview(1L, 0, "AIGC 服务不可用");
        verify(persistenceService, never()).completeReview(any(), anyInt(), any());
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 验证领取答案之前发生异常时不会错误回写失败状态
     */
    @Test
    @DisplayName("领取答案失败时不应错误更新答案状态")
    void shouldNotFailAnswersWhenClaimOperationThrowsException() {
        when(persistenceService.claimPendingAnswers(1L, 0))
                .thenThrow(new IllegalStateException("练习会话不存在"));

        taskService.reviewAsync(1L, 10L, 0);

        verify(persistenceService, never()).failReview(any(), anyInt(), any());
        verify(aigcTaskClient, never()).gradeSubjective(any());
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 验证无效 AI 建议分不会被保存为正常结果
     */
    @Test
    @DisplayName("AI 建议分越界时应标记评估失败")
    void shouldFailWhenSuggestedScoreIsOutOfRange() {
        PracticeAnswer answer = pendingAnswer();
        SubjectiveGradingResultDTO result = gradingResult();
        result.setSuggestedScore(11);
        when(persistenceService.claimPendingAnswers(1L, 0)).thenReturn(List.of(answer));
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(subjectiveQuestion()));
        when(aigcTaskClient.gradeSubjective(any())).thenReturn(result);

        taskService.reviewAsync(1L, 10L, 0);

        verify(persistenceService).failReview(1L, 0, "AI 评估结果中的建议分无效");
        verify(persistenceService, never()).completeReview(any(), anyInt(), any());
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 创建一个待执行 AI 评估的答案
     *
     * @return 练习答案
     */
    private PracticeAnswer pendingAnswer() {
        return new PracticeAnswer()
                .setId(100L)
                .setSessionId(1L)
                .setPracticeQuestionId(200L)
                .setQuestionId(300L)
                .setStudentAnswer("学生作答");
    }

    /**
     * 创建一个主观题快照
     *
     * @return 主观题快照
     */
    private PracticeQuestion subjectiveQuestion() {
        return new PracticeQuestion()
                .setId(200L)
                .setSessionId(1L)
                .setQuestionId(300L)
                .setName("请说明事务的 ACID 特性")
                .setStandardAnswer("原子性、一致性、隔离性和持久性")
                .setAnalysis("每个特性各占一定分值")
                .setScore(10);
    }

    /**
     * 验证 AI 返回的超长得分点不会写入答案记录
     */
    @Test
    @DisplayName("AI 返回超长得分点时应标记评估失败")
    void shouldFailWhenAiPointIsTooLong() {
        PracticeAnswer answer = pendingAnswer();
        SubjectiveGradingResultDTO result = gradingResult();
        result.setMatchedPoints(List.of("a".repeat(2001)));
        when(persistenceService.claimPendingAnswers(1L, 0)).thenReturn(List.of(answer));
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(subjectiveQuestion()));
        when(aigcTaskClient.gradeSubjective(any())).thenReturn(result);

        taskService.reviewAsync(1L, 10L, 0);

        verify(persistenceService).failReview(1L, 0, "AI 评估结果中的命中得分点单条内容超过长度限制");
        verify(persistenceService, never()).completeReview(any(), anyInt(), any());
    }

    /**
     * 验证 AI 返回的超长改进建议不会写入答案记录
     */
    @Test
    @DisplayName("AI 返回超长改进建议时应标记评估失败")
    void shouldFailWhenAiSuggestionIsTooLong() {
        PracticeAnswer answer = pendingAnswer();
        SubjectiveGradingResultDTO result = gradingResult();
        result.setImprovementSuggestion("a".repeat(5001));
        when(persistenceService.claimPendingAnswers(1L, 0)).thenReturn(List.of(answer));
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(subjectiveQuestion()));
        when(aigcTaskClient.gradeSubjective(any())).thenReturn(result);

        taskService.reviewAsync(1L, 10L, 0);

        verify(persistenceService).failReview(1L, 0, "AI 评估结果中的改进建议超过长度限制");
        verify(persistenceService, never()).completeReview(any(), anyInt(), any());
    }
    /**
     * 创建一个合法的 AI 主观题评估结果
     *
     * @return AI 评估结果
     */
    private SubjectiveGradingResultDTO gradingResult() {
        SubjectiveGradingResultDTO result = new SubjectiveGradingResultDTO();
        result.setSuggestedScore(8);
        result.setTotalScore(10);
        result.setMatchedPoints(List.of("得分点一"));
        result.setMissingPoints(null);
        result.setIncorrectStatements(null);
        result.setImprovementSuggestion("补充隔离级别示例");
        result.setConfidence(new BigDecimal("0.85"));
        result.setManualReviewRecommended(false);
        result.setRubricVersion("模型返回版本");
        return result;
    }
}