package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.dto.AiQuestionDraftUpdateDTO;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.AiQuestionDraft;
import com.tianji.exam.enums.AiQuestionBatchStatus;
import com.tianji.exam.enums.AiQuestionDraftStatus;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.mapper.AiQuestionDraftMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 题目草稿服务测试
 */
@ExtendWith(MockitoExtension.class)
class AiQuestionDraftServiceImplTest {

    @Mock
    private AiQuestionBatchMapper batchMapper;
    @Mock
    private AiQuestionDraftMapper draftMapper;

    private AiQuestionDraftServiceImpl draftService;

    /**
     * 创建待测试服务并准备当前用户。
     */
    @BeforeEach
    void setUp() {
        TableInfo tableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiQuestionDraft.class);
        LambdaUtils.installCache(tableInfo);
        draftService = new TestableAiQuestionDraftService(batchMapper);
        ReflectionTestUtils.setField(draftService, "baseMapper", draftMapper);
        UserContext.setUser(100L);
    }

    /**
     * 每个测试结束后清理用户上下文。
     */
    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    /**
     * 验证删除未发布草稿后会刷新批次统计。
     */
    @Test
    @DisplayName("删除未发布草稿应刷新批次统计")
    void shouldDeleteUnpublishedDraftAndRecalculateBatch() {
        prepareOwnedDraft(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
        when(draftMapper.deleteById(1L)).thenReturn(1);
        when(draftMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(batchMapper.updateById(any(AiQuestionBatch.class))).thenReturn(1);

        draftService.deleteDraft(1L);

        verify(draftMapper).deleteById(1L);
        verify(batchMapper).updateById(any(AiQuestionBatch.class));
    }

    /**
     * 验证已发布草稿不允许删除。
     */
    @Test
    @DisplayName("已发布草稿不应允许删除")
    void shouldRejectDeletingPublishedDraft() {
        prepareOwnedDraft(AiQuestionDraftStatus.PUBLISHED.name());

        assertThatThrownBy(() -> draftService.deleteDraft(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("已发布的题目草稿不能删除");

        verify(draftMapper, never()).deleteById(1L);
        verify(batchMapper, never()).updateById(any(AiQuestionBatch.class));
    }

    /**
     * 验证草稿删除失败时抛出数据库异常且不刷新批次统计。
     */
    @Test
    @DisplayName("草稿删除失败时不应刷新批次统计")
    void shouldStopWhenDraftDeleteFails() {
        prepareOwnedDraft(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
        when(draftMapper.deleteById(1L)).thenReturn(0);

        assertThatThrownBy(() -> draftService.deleteDraft(1L))
                .isInstanceOf(DbException.class)
                .hasMessage("AI 题目草稿删除失败");

        verify(batchMapper, never()).updateById(any(AiQuestionBatch.class));
    }

    @Test
    @DisplayName("草稿内容更新失败时应中止后续处理")
    void shouldStopWhenDraftUpdateFails() {
        prepareOwnedDraft(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
        when(draftMapper.updateDraftContent(any(AiQuestionDraft.class))).thenReturn(0);

        assertThatThrownBy(() -> draftService.updateDraft(1L, validUpdateRequest()))
                .isInstanceOf(DbException.class)
                .hasMessage("AI 题目草稿更新失败");

        verify(batchMapper, never()).updateById(any(AiQuestionBatch.class));
    }

    /**
     * 验证确认状态写入失败时抛出数据库异常且不刷新批次统计。
     */
    @Test
    @DisplayName("草稿确认失败时应中止后续处理")
    void shouldStopWhenDraftConfirmationFails() {
        prepareOwnedDraft(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
        when(draftMapper.updateById(any(AiQuestionDraft.class))).thenReturn(0);

        assertThatThrownBy(() -> draftService.confirm(1L))
                .isInstanceOf(DbException.class)
                .hasMessage("AI 题目草稿确认失败");

        verify(batchMapper, never()).updateById(any(AiQuestionBatch.class));
    }

    /**
     * 验证驳回状态写入失败时抛出数据库异常且不刷新批次统计。
     */
    @Test
    @DisplayName("草稿驳回失败时应中止后续处理")
    void shouldStopWhenDraftRejectionFails() {
        prepareOwnedDraft(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
        when(draftMapper.updateById(any(AiQuestionDraft.class))).thenReturn(0);

        assertThatThrownBy(() -> draftService.reject(1L, "题目表述不准确"))
                .isInstanceOf(DbException.class)
                .hasMessage("AI 题目草稿驳回失败");

        verify(batchMapper, never()).updateById(any(AiQuestionBatch.class));
    }

    /**
     * 验证批次统计刷新失败时抛出数据库异常以回滚草稿确认事务。
     */
    @Test
    @DisplayName("批次统计刷新失败时应回滚草稿确认")
    void shouldFailWhenBatchStatisticsRefreshFails() {
        prepareOwnedDraft(AiQuestionDraftStatus.PENDING_CONFIRMATION.name());
        when(draftMapper.updateById(any(AiQuestionDraft.class))).thenReturn(1);
        when(draftMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new AiQuestionDraft().setId(1L).setBatchId(2L)
                        .setStatus(AiQuestionDraftStatus.CONFIRMED.name())));
        when(batchMapper.updateById(any(AiQuestionBatch.class))).thenReturn(0);

        assertThatThrownBy(() -> draftService.confirm(1L))
                .isInstanceOf(DbException.class)
                .hasMessage("AI 出题批次统计刷新失败");
    }

    /**
     * 为单元测试提供 MyBatis-Plus 实体元数据，避免依赖真实 Mapper 代理。
     */
    private static class TestableAiQuestionDraftService extends AiQuestionDraftServiceImpl {

        /**
         * 创建测试服务。
         *
         * @param batchMapper 批次 Mapper
         */
        private TestableAiQuestionDraftService(AiQuestionBatchMapper batchMapper) {
            super(batchMapper);
        }

        /**
         * 返回草稿实体类型。
         *
         * @return 草稿实体类型
         */
        @Override
        public Class<AiQuestionDraft> getEntityClass() {
            return AiQuestionDraft.class;
        }

        /**
         * 返回草稿 Mapper 类型。
         *
         * @return 草稿 Mapper 类型
         */
        @Override
        public Class<AiQuestionDraftMapper> getMapperClass() {
            return AiQuestionDraftMapper.class;
        }
    }

    /**
     * 准备当前用户拥有的草稿及所属批次。
     *
     * @param status 草稿状态
     */
    private void prepareOwnedDraft(String status) {
        AiQuestionDraft draft = new AiQuestionDraft()
                .setId(1L)
                .setBatchId(2L)
                .setStatus(status);
        AiQuestionBatch batch = new AiQuestionBatch()
                .setId(2L)
                .setCreater(100L)
                .setQuestionTypes(List.of(1))
                .setCourseId(10L)
                .setScopeType("SECTION")
                .setScopeId(20L)
                .setTargetBizId(30L)
                .setStatus(AiQuestionBatchStatus.PENDING_CONFIRMATION.name());
        when(draftMapper.selectById(1L)).thenReturn(draft);
        when(batchMapper.selectById(2L)).thenReturn(batch);
        when(batchMapper.lockById(2L)).thenReturn(2L);
    }

    /**
     * 构造合法的选择题编辑参数。
     *
     * @return 草稿编辑参数
     */
    private AiQuestionDraftUpdateDTO validUpdateRequest() {
        AiQuestionDraftUpdateDTO dto = new AiQuestionDraftUpdateDTO();
        dto.setName("Java 中继承的关键字是什么？");
        dto.setType(1);
        dto.setDifficulty(2);
        dto.setScore(5);
        dto.setOptions(List.of("extends", "implements"));
        dto.setAnswer("0");
        dto.setAnalysis("类继承使用 extends 关键字");
        dto.setKnowledgePoints(List.of("继承"));
        return dto;
    }
}
