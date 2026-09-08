package com.tianji.exam.service.impl;

import com.tianji.api.client.aigc.AigcTaskClient;
import com.tianji.api.dto.aigc.AiQuestionGenerateRequestDTO;
import com.tianji.api.dto.aigc.AiGeneratedQuestionDTO;
import com.tianji.api.dto.aigc.AiQuestionGenerateResultDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.enums.AiQuestionBatchStatus;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.service.AiQuestionGenerationPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 题目异步生成任务服务测试
 */
@ExtendWith(MockitoExtension.class)
class AiQuestionGenerationTaskServiceImplTest {

    @Mock
    private AiQuestionBatchMapper batchMapper;
    @Mock
    private AiQuestionGenerationPersistenceService persistenceService;
    @Mock
    private AigcTaskClient aigcTaskClient;
    @InjectMocks
    private AiQuestionGenerationTaskServiceImpl taskService;

    /**
     * 每个测试结束后清理线程中的用户上下文。
     */
    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    /**
     * 验证正常生成时会进入持久化阶段并清理用户上下文。
     */
    @Test
    @DisplayName("生成成功后应持久化题目草稿")
    void shouldPersistGeneratedQuestions() {
        AiQuestionBatch batch = batch();
        AiGeneratedQuestionDTO question = new AiGeneratedQuestionDTO();
        AiQuestionGenerateResultDTO result = new AiQuestionGenerateResultDTO();
        result.setQuestions(List.of(question));
        when(batchMapper.selectById(1L)).thenReturn(batch);
        when(batchMapper.startGeneration(eq(1L), eq(2), any())).thenReturn(1);
        when(batchMapper.startValidation(eq(1L), eq(2), any())).thenReturn(1);
        when(aigcTaskClient.generateQuestions(any())).thenReturn(result);

        taskService.generateAsync(1L);

        ArgumentCaptor<AiQuestionGenerateRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(AiQuestionGenerateRequestDTO.class);
        verify(aigcTaskClient).generateQuestions(requestCaptor.capture());
        verify(persistenceService).persist(1L, 2, result.getQuestions());
        assertThat(requestCaptor.getValue().getScopeSectionIds()).containsExactly(21L, 22L);
        assertThat(requestCaptor.getValue().getScopeSectionNames()).containsExactly("第一小节", "第二小节");
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 验证模型返回题目数量不足时会将本次生成标记为失败，避免静默生成不完整批次。
     */
    @Test
    @DisplayName("模型返回题目数量不一致时应标记批次失败")
    void shouldMarkBatchFailedWhenQuestionCountDoesNotMatch() {
        AiQuestionBatch batch = batch().setQuestionCount(2);
        AiQuestionGenerateResultDTO result = new AiQuestionGenerateResultDTO();
        result.setQuestions(List.of(new AiGeneratedQuestionDTO()));
        when(batchMapper.selectById(1L)).thenReturn(batch);
        when(batchMapper.startGeneration(eq(1L), eq(2), any())).thenReturn(1);
        when(aigcTaskClient.generateQuestions(any())).thenReturn(result);

        taskService.generateAsync(1L);

        verify(batchMapper).markGenerationFailed(eq(1L), eq(2),
                eq("AI 返回题目数量与请求数量不一致"), any());
        verify(batchMapper, never()).startValidation(any(), anyInt(), any());
        verify(persistenceService, never()).persist(any(), anyInt(), any());
    }

    /**
     * 验证未取得批次状态迁移权时不会重复调用大模型。
     */
    @Test
    @DisplayName("重复触发同一批次时应跳过生成")
    void shouldSkipWhenBatchHasAlreadyStarted() {
        when(batchMapper.selectById(1L)).thenReturn(batch());
        when(batchMapper.startGeneration(eq(1L), eq(2), any())).thenReturn(0);

        taskService.generateAsync(1L);

        verify(aigcTaskClient, never()).generateQuestions(any());
        verify(persistenceService, never()).persist(any(), anyInt(), any());
    }

    /**
     * 验证远程生成失败时只在任务边界回写失败状态。
     */
    @Test
    @DisplayName("生成异常时应回写失败状态")
    void shouldMarkBatchFailedWhenGenerationThrowsException() {
        when(batchMapper.selectById(1L)).thenReturn(batch());
        when(batchMapper.startGeneration(eq(1L), eq(2), any())).thenReturn(1);
        when(aigcTaskClient.generateQuestions(any())).thenThrow(new IllegalStateException("模型调用失败"));

        taskService.generateAsync(1L);

        verify(batchMapper).startGeneration(eq(1L), eq(2), any());
        verify(batchMapper).markGenerationFailed(eq(1L), eq(2), eq("模型调用失败"), any());
        verify(persistenceService, never()).persist(any(), anyInt(), any());
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 验证旧执行代际在进入校验阶段失败后，不会持久化结果或覆盖新代际状态。
     */
    @Test
    @DisplayName("旧执行代际不能推进新代际任务")
    void shouldSkipPersistenceWhenGenerationFenceIsLost() {
        AiQuestionBatch batch = batch();
        AiQuestionGenerateResultDTO result = new AiQuestionGenerateResultDTO();
        result.setQuestions(List.of(new AiGeneratedQuestionDTO()));
        when(batchMapper.selectById(1L)).thenReturn(batch);
        when(batchMapper.startGeneration(eq(1L), eq(2), any())).thenReturn(1);
        when(batchMapper.startValidation(eq(1L), eq(2), any())).thenReturn(0);
        when(aigcTaskClient.generateQuestions(any())).thenReturn(result);

        taskService.generateAsync(1L);

        verify(persistenceService, never()).persist(any(), anyInt(), any());
        verify(batchMapper, never()).markGenerationFailed(any(), anyInt(), any(), any());
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 构造处于待生成状态的测试批次。
     *
     * @return 测试批次
     */
    private AiQuestionBatch batch() {
        return new AiQuestionBatch()
                .setId(1L)
                .setCreater(100L)
                .setStatus(AiQuestionBatchStatus.CREATED.name())
                .setRetryCount(2)
                .setCourseId(10L)
                .setScopeId(20L)
                .setScopeSectionIds(List.of(21L, 22L))
                .setScopeSectionNames(List.of("第一小节", "第二小节"))
                .setTargetBizId(30L)
                .setQuestionTypes(List.of(1));
    }
}