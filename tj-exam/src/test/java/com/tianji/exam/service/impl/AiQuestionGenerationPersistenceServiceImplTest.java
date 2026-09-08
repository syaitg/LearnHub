package com.tianji.exam.service.impl;

import com.tianji.api.dto.aigc.AiGeneratedQuestionDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.AiQuestionDraft;
import com.tianji.exam.enums.AiQuestionBatchStatus;
import com.tianji.exam.enums.AiQuestionDraftStatus;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.mapper.AiQuestionDraftMapper;
import com.tianji.exam.service.IAiQuestionDraftService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 题目生成结果持久化服务测试
 */
@ExtendWith(MockitoExtension.class)
class AiQuestionGenerationPersistenceServiceImplTest {

    @Mock
    private AiQuestionBatchMapper batchMapper;
    @Mock
    private AiQuestionDraftMapper draftMapper;
    @Mock
    private IAiQuestionDraftService draftService;
    @InjectMocks
    private AiQuestionGenerationPersistenceServiceImpl persistenceService;

    /**
     * 验证合法题目会保存为待确认草稿，并使用批次默认难度和分值。
     */
    @Test
    @DisplayName("合法生成结果应保存为待确认草稿")
    void shouldPersistValidDraft() {
        AiQuestionBatch batch = validatingBatch();
        AiGeneratedQuestionDTO question = question(null, 1);
        prepareBatch(batch);
        prepareEmptyDrafts();
        when(batchMapper.completeValidation(eq(1L), eq(2), eq(AiQuestionBatchStatus.PENDING_CONFIRMATION.name()),
                eq(1), eq(1), eq(0), any())).thenReturn(1);

        persistenceService.persist(1L, 2, List.of(question));

        ArgumentCaptor<List<AiQuestionDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(draftService).saveBatch(captor.capture());
        AiQuestionDraft draft = captor.getValue().get(0);
        assertThat(draft.getStatus()).isEqualTo(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
        assertThat(draft.getDifficulty()).isEqualTo(2);
        assertThat(draft.getScore()).isEqualTo(5);
        verify(batchMapper).completeValidation(eq(1L), eq(2), eq(AiQuestionBatchStatus.PENDING_CONFIRMATION.name()),
                eq(1), eq(1), eq(0), any());
    }

    /**
     * 验证结构不合法的题目会保存为无效草稿，不会参与重复查询。
     */
    @Test
    @DisplayName("不合法生成结果应保存为无效草稿")
    void shouldPersistInvalidDraft() {
        AiQuestionBatch batch = validatingBatch();
        AiGeneratedQuestionDTO question = question("", 1);
        prepareBatch(batch);
        prepareEmptyDrafts();
        when(batchMapper.completeValidation(eq(1L), eq(2), eq(AiQuestionBatchStatus.INVALID.name()),
                eq(1), eq(0), eq(0), any())).thenReturn(1);

        persistenceService.persist(1L, 2, List.of(question));

        ArgumentCaptor<List<AiQuestionDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(draftService).saveBatch(captor.capture());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo(AiQuestionDraftStatus.INVALID.name());
        verify(draftMapper, never()).countPotentialDuplicate(any(), any(), any(), any(), any(), any());
    }

    /**
     * 验证同一批次内的重复题目只保留第一题为有效草稿。
     */
    @Test
    @DisplayName("同一批次内的重复题目应标记为重复")
    void shouldMarkDuplicateWithinBatch() {
        AiQuestionBatch batch = validatingBatch();
        prepareBatch(batch);
        prepareEmptyDrafts();
        when(batchMapper.completeValidation(eq(1L), eq(2), eq(AiQuestionBatchStatus.PENDING_CONFIRMATION.name()),
                eq(2), eq(1), eq(1), any())).thenReturn(1);

        persistenceService.persist(1L, 2, List.of(question("相同题目", 1), question("相同题目", 1)));

        ArgumentCaptor<List<AiQuestionDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(draftService).saveBatch(captor.capture());
        assertThat(captor.getValue()).extracting(AiQuestionDraft::getStatus)
                .containsExactly(AiQuestionDraftStatus.PENDING_CONFIRMATION.name(),
                        AiQuestionDraftStatus.DUPLICATE.name());
        verify(draftMapper).countPotentialDuplicate(any(), eq(1L), eq(10L), eq("SECTION"), eq(20L), eq(30L));
    }

    /**
     * 验证课程范围内已经存在的相同题目会被标记为重复。
     */
    @Test
    @DisplayName("课程范围内已有相同题目时应标记为重复")
    void shouldMarkDuplicateExistingQuestion() {
        AiQuestionBatch batch = validatingBatch();
        prepareBatch(batch);
        prepareEmptyDrafts();
        when(draftMapper.countPotentialDuplicate(any(), eq(1L), eq(10L), eq("SECTION"), eq(20L), eq(30L)))
                .thenReturn(1L);
        when(batchMapper.completeValidation(eq(1L), eq(2), eq(AiQuestionBatchStatus.INVALID.name()),
                eq(1), eq(0), eq(1), any())).thenReturn(1);

        persistenceService.persist(1L, 2, List.of(question("已存在题目", 1)));

        ArgumentCaptor<List<AiQuestionDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(draftService).saveBatch(captor.capture());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo(AiQuestionDraftStatus.DUPLICATE.name());
        assertThat(captor.getValue().get(0).getValidationMessage()).contains("重复");
    }

    /**
     * 验证模型返回题目数量超过批次请求上限时拒绝保存。
     */
    @Test
    @DisplayName("模型返回题目过多时应拒绝保存")
    void shouldRejectWhenGeneratedQuestionCountExceedsLimit() {
        AiQuestionBatch batch = validatingBatch().setQuestionCount(1);
        prepareBatch(batch);

        assertThatThrownBy(() -> persistenceService.persist(1L, 2,
                List.of(question("第一题", 1), question("第二题", 1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("超过本批次请求上限");

        verify(draftMapper, never()).countByBatchId(any());
        verify(draftService, never()).saveBatch(any());
    }

    /**
     * 验证非校验状态的批次不会重复保存模型结果。
     */
    @Test
    @DisplayName("非校验状态批次应跳过持久化")
    void shouldSkipWhenBatchIsNotValidating() {
        AiQuestionBatch batch = validatingBatch().setStatus(AiQuestionBatchStatus.GENERATING.name());
        prepareBatch(batch);

        persistenceService.persist(1L, 2, List.of(question("不会保存", 1)));

        verify(draftMapper, never()).countByBatchId(any());
        verify(draftService, never()).saveBatch(any());
        verify(batchMapper, never()).completeValidation(any(), anyInt(), any(), anyInt(), anyInt(), anyInt(), any());
    }

    /**
     * 验证旧执行代际不会把题目结果写入新一代重试任务。
     */
    @Test
    @DisplayName("重试次数不一致时应跳过持久化")
    void shouldSkipWhenRetryGenerationDoesNotMatch() {
        AiQuestionBatch batch = validatingBatch();
        prepareBatch(batch);

        persistenceService.persist(1L, 1, List.of(question("旧代际题目", 1)));

        verify(draftMapper, never()).countByBatchId(any());
        verify(draftService, never()).saveBatch(any());
        verify(batchMapper, never()).completeValidation(any(), anyInt(), any(),
                anyInt(), anyInt(), anyInt(), any());
    }

    /**
     * 验证已经存在草稿的批次不会再次保存生成结果。
     */
    @Test
    @DisplayName("已经保存草稿的批次不能重复持久化")
    void shouldRejectWhenBatchAlreadyHasDrafts() {
        AiQuestionBatch batch = validatingBatch();
        prepareBatch(batch);
        when(draftMapper.countByBatchId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> persistenceService.persist(1L, 2, List.of(question("重复保存", 1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("当前批次已经保存过题目草稿");
        verify(draftService, never()).saveBatch(any());
    }

    /**
     * 准备批次锁定、查询和草稿数量查询的公共测试桩。
     *
     * @param batch 测试批次
     */
    private void prepareBatch(AiQuestionBatch batch) {
        when(batchMapper.lockById(1L)).thenReturn(1L);
        when(batchMapper.selectById(1L)).thenReturn(batch);
    }

    /**
     * 准备尚未保存草稿的公共测试桩。
     */
    private void prepareEmptyDrafts() {
        when(draftMapper.countByBatchId(1L)).thenReturn(0L);
        when(draftService.saveBatch(any())).thenReturn(true);
    }

    /**
     * 构造处于结构校验阶段的测试批次。
     *
     * @return 测试批次
     */
    private AiQuestionBatch validatingBatch() {
        return new AiQuestionBatch()
                .setId(1L)
                .setCourseId(10L)
                .setScopeType("SECTION")
                .setScopeId(20L)
                .setTargetBizId(30L)
                .setQuestionTypes(List.of(1))
                .setQuestionCount(5)
                .setDifficulty(2)
                .setScore(5)
                .setCreater(100L)
                .setStatus(AiQuestionBatchStatus.VALIDATING.name())
                .setRetryCount(2);
    }

    /**
     * 构造一条选择题测试数据。
     *
     * @param name 题干
     * @param type 题型
     * @return AI 生成题目
     */
    private AiGeneratedQuestionDTO question(String name, Integer type) {
        AiGeneratedQuestionDTO question = new AiGeneratedQuestionDTO();
        question.setName(name == null ? "测试题目" : name);
        question.setType(type);
        question.setOptions(List.of("选项一", "选项二"));
        question.setAnswer("0");
        question.setAnalysis("答案解析");
        return question;
    }
}