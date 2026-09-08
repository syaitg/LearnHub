package com.tianji.exam.service.impl;

import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.dto.AiQuestionBatchCreateDTO;
import com.tianji.exam.domain.event.AiQuestionGenerationRequestedEvent;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.AiQuestionDraft;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.service.IAiQuestionDraftService;
import com.tianji.exam.service.IAiQuestionOriginService;
import com.tianji.exam.service.IQuestionBizService;
import com.tianji.exam.service.IQuestionDetailService;
import com.tianji.exam.service.IQuestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 出题批次服务测试
 */
@ExtendWith(MockitoExtension.class)
class AiQuestionBatchServiceImplTest {

    @Mock
    private CourseClient courseClient;
    @Mock
    private CatalogueClient catalogueClient;
    @Mock
    private IAiQuestionDraftService draftService;
    @Mock
    private IQuestionService questionService;
    @Mock
    private IQuestionDetailService questionDetailService;
    @Mock
    private IQuestionBizService questionBizService;
    @Mock
    private IAiQuestionOriginService originService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AiQuestionBatchMapper batchMapper;

    private AiQuestionBatchServiceImpl batchService;

    /**
     * 创建待测试服务并注入批次 Mapper。
     */
    @BeforeEach
    void setUp() {
        batchService = new AiQuestionBatchServiceImpl(
                courseClient, catalogueClient, draftService, questionService,
                questionDetailService, questionBizService, originService, eventPublisher);
        ReflectionTestUtils.setField(batchService, "baseMapper", batchMapper);
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
     * 验证章级综合测试会保存规范化后的小节范围。
     */
    @Test
    @DisplayName("章级出题应校验并保存所选小节")
    void shouldCreateChapterBatchWithSelectedSections() {
        prepareCourseAndChapter();
        when(catalogueClient.queryCatalogueDetails(List.of(11L, 12L)))
                .thenReturn(List.of(
                        catalogue(12L, "第二节", 1L, 2, 10L),
                        catalogue(11L, "第一节", 1L, 2, 10L)));
        when(batchMapper.insert(any(AiQuestionBatch.class))).thenReturn(1);

        Long batchId = batchService.createBatch(chapterRequest(List.of(12L, 11L, 12L)));

        ArgumentCaptor<AiQuestionBatch> captor = ArgumentCaptor.forClass(AiQuestionBatch.class);
        verify(batchMapper).insert(captor.capture());
        AiQuestionBatch batch = captor.getValue();
        assertThat(batchId).isEqualTo(batch.getId());
        assertThat(batch.getScopeType()).isEqualTo("CHAPTER");
        assertThat(batch.getScopeSectionIds()).containsExactly(11L, 12L);
        assertThat(batch.getScopeSectionNames()).containsExactly("第一节", "第二节");
        verify(eventPublisher).publishEvent(any(AiQuestionGenerationRequestedEvent.class));
    }

    /**
     * 验证章级出题不能选择其他章节的小节。
     */
    @Test
    @DisplayName("章级出题选择其他章节小节时应拒绝")
    void shouldRejectSectionOutsideChapter() {
        prepareCourseAndChapter();
        when(catalogueClient.queryCatalogueDetails(List.of(11L)))
                .thenReturn(List.of(catalogue(11L, "其他章小节", 1L, 2, 99L)));

        assertThatThrownBy(() -> batchService.createBatch(chapterRequest(List.of(11L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不属于当前章节");

        verify(batchMapper, never()).insert(any(AiQuestionBatch.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 验证小节级出题不能混入章级小节选择参数。
     */
    @Test
    @DisplayName("小节级出题额外指定章内小节时应拒绝")
    void shouldRejectSelectedSectionsForSectionScope() {
        when(courseClient.baseInfo(1L, true)).thenReturn(course());
        when(catalogueClient.queryCatalogueDetail(11L))
                .thenReturn(catalogue(11L, "第一节", 1L, 2, 10L));
        when(catalogueClient.queryCatalogueDetail(20L))
                .thenReturn(catalogue(20L, "第一节随堂测验", 1L, 3, 10L));
        AiQuestionBatchCreateDTO request = chapterRequest(List.of(11L));
        request.setScopeType("SECTION");
        request.setScopeId(11L);

        assertThatThrownBy(() -> batchService.createBatch(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能额外指定");

        verify(catalogueClient, never()).queryCatalogueDetails(any());
        verify(batchMapper, never()).insert(any(AiQuestionBatch.class));
    }

    /**
     * 验证正式发布前会重新校验课程所有权
     */
    @Test
    @DisplayName("课程所有权变化后发布应被拒绝")
    void shouldRejectPublishWhenCourseOwnershipChanged() {
        AiQuestionBatch batch = publishContextBatch();
        CourseBaseInfoDTO changedCourse = course();
        changedCourse.setCreater(999L);
        when(courseClient.baseInfo(1L, true)).thenReturn(changedCourse);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                batchService, "revalidatePublishContext", batch, 100L))
                .isInstanceOf(com.tianji.common.exceptions.ForbiddenException.class)
                .hasMessageContaining("无权");

        verify(catalogueClient, never()).queryCatalogueDetail(any());
    }

    /**
     * 验证正式发布前会重新校验目标目录类型
     */
    @Test
    @DisplayName("目标目录不再是练习目录时发布应被拒绝")
    void shouldRejectPublishWhenTargetCatalogueTypeChanged() {
        AiQuestionBatch batch = publishContextBatch();
        when(courseClient.baseInfo(1L, true)).thenReturn(course());
        when(catalogueClient.queryCatalogueDetail(10L))
                .thenReturn(catalogue(10L, "第一章", 1L, 1, 0L));
        when(catalogueClient.queryCatalogueDetail(20L))
                .thenReturn(catalogue(20L, "已变更目录", 1L, 2, 10L));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                batchService, "revalidatePublishContext", batch, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("练习目录");
    }

    /**
     * 验证正式发布前会重新校验章级批次的小节归属
     */
    @Test
    @DisplayName("已选小节不再属于原章节时发布应被拒绝")
    void shouldRejectPublishWhenSelectedSectionMoved() {
        AiQuestionBatch batch = publishContextBatch().setScopeSectionIds(List.of(11L));
        prepareCourseAndChapter();
        when(catalogueClient.queryCatalogueDetails(List.of(11L)))
                .thenReturn(List.of(catalogue(11L, "已移动小节", 1L, 2, 99L)));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                batchService, "revalidatePublishContext", batch, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不属于当前章节");
    }
    /**
     * 验证正式题目保存失败时会中止发布链路。
     */
    @Test
    @DisplayName("正式题目保存失败时应中止发布")
    void shouldStopPublishingWhenQuestionSaveFails() {
        AiQuestionBatch batch = new AiQuestionBatch()
                .setId(1L)
                .setTargetBizId(20L)
                .setCateId1(1L)
                .setCateId2(2L)
                .setCateId3(3L);
        AiQuestionDraft draft = new AiQuestionDraft()
                .setId(10L)
                .setName("什么是 Java 继承？")
                .setType(5)
                .setDifficulty(2)
                .setScore(10)
                .setAnswer("子类复用父类成员的机制")
                .setAnalysis("考查继承概念");
        when(questionService.saveBatch(any())).thenReturn(false);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                batchService, "publishConfirmedDrafts", batch, List.of(draft), 100L))
                .isInstanceOf(DbException.class)
                .hasMessage("正式题目保存失败");

        verify(questionDetailService, never()).saveBatch(any());
        verify(questionBizService, never()).saveBatch(any());
        verify(originService, never()).saveBatch(any());
        verify(draftService, never()).updateBatchById(any());
    }

    /**
     * 创建用于发布上下文复核的批次
     *
     * @return 待复核批次
     */
    private AiQuestionBatch publishContextBatch() {
        return new AiQuestionBatch()
                .setId(1L)
                .setCourseId(1L)
                .setCourseName("Java 基础")
                .setScopeType("CHAPTER")
                .setScopeId(10L)
                .setScopeSectionIds(List.of())
                .setTargetBizId(20L)
                .setCateId1(1L)
                .setCateId2(2L)
                .setCateId3(3L);
    }
    /**
     * 准备课程、章节和章级测试目录。
     */
    private void prepareCourseAndChapter() {
        when(courseClient.baseInfo(1L, true)).thenReturn(course());
        when(catalogueClient.queryCatalogueDetail(10L))
                .thenReturn(catalogue(10L, "第一章", 1L, 1, 0L));
        when(catalogueClient.queryCatalogueDetail(20L))
                .thenReturn(catalogue(20L, "第一章综合测试", 1L, 3, 10L));
    }

    /**
     * 创建章级出题请求。
     *
     * @param sectionIds 章内小节 ID 列表
     * @return 出题请求
     */
    private AiQuestionBatchCreateDTO chapterRequest(List<Long> sectionIds) {
        AiQuestionBatchCreateDTO dto = new AiQuestionBatchCreateDTO();
        dto.setCourseId(1L);
        dto.setScopeType("CHAPTER");
        dto.setScopeId(10L);
        dto.setScopeSectionIds(sectionIds);
        dto.setSourceType("MANUAL_TOPIC");
        dto.setTargetBizId(20L);
        dto.setKnowledgePoints(List.of("继承", "多态"));
        dto.setQuestionTypes(List.of(1, 5));
        dto.setQuestionCount(5);
        dto.setDifficulty(2);
        dto.setScore(10);
        return dto;
    }

    /**
     * 创建可操作课程信息。
     *
     * @return 课程信息
     */
    private CourseBaseInfoDTO course() {
        CourseBaseInfoDTO course = new CourseBaseInfoDTO();
        course.setId(1L);
        course.setName("Java 基础");
        course.setCreater(100L);
        course.setFirstCateId(1L);
        course.setSecondCateId(2L);
        course.setThirdCateId(3L);
        return course;
    }

    /**
     * 创建目录业务详情。
     *
     * @param id 目录 ID
     * @param name 目录名称
     * @param courseId 课程 ID
     * @param type 目录类型
     * @param parentId 父目录 ID
     * @return 目录详情
     */
    private CatalogueDetailDTO catalogue(Long id, String name, Long courseId, Integer type, Long parentId) {
        CatalogueDetailDTO catalogue = new CatalogueDetailDTO();
        catalogue.setId(id);
        catalogue.setName(name);
        catalogue.setCourseId(courseId);
        catalogue.setType(type);
        catalogue.setParentCatalogueId(parentId);
        return catalogue;
    }
}
