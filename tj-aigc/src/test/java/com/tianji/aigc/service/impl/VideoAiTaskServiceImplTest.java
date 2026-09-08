package com.tianji.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.dto.ChapterVideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.dto.VideoAiTaskCreateDTO;
import com.tianji.aigc.domain.dto.VideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.event.VideoAiTaskRequestedEvent;
import com.tianji.aigc.domain.model.VideoKnowledgePoint;
import com.tianji.aigc.domain.po.VideoAiTask;
import com.tianji.aigc.enums.VideoAiTaskStatus;
import com.tianji.aigc.mapper.VideoAiTaskMapper;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.media.MediaClient;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.api.dto.exam.AiQuestionBatchCreateRequestDTO;
import com.tianji.api.dto.media.MediaAiInfoDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.UserContext;
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
 * 视频 AI 任务业务服务测试
 */
@ExtendWith(MockitoExtension.class)
class VideoAiTaskServiceImplTest {

    @Mock
    private VideoAiTaskMapper taskMapper;
    @Mock
    private CatalogueClient catalogueClient;
    @Mock
    private CourseClient courseClient;
    @Mock
    private MediaClient mediaClient;
    @Mock
    private ExamClient examClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private VideoAiProperties properties;
    private VideoAiTaskServiceImpl taskService;

    /**
     * 初始化视频 AI 任务业务服务
     */
    @BeforeEach
    void setUp() {
        properties = new VideoAiProperties();
        properties.setProvider("audio-service");
        properties.setProviderVersion("v1");
        properties.setConfigVersion("video-ai-v1");
        taskService = new VideoAiTaskServiceImpl(
                catalogueClient, courseClient, mediaClient, examClient, properties, eventPublisher);
        ReflectionTestUtils.setField(taskService, "baseMapper", taskMapper);
        UserContext.setUser(100L);
    }

    /**
     * 清理测试线程中的用户上下文
     */
    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /**
     * 验证课程创建者能够创建视频 AI 任务并在提交后发布执行事件
     */
    @Test
    @DisplayName("课程创建者应能创建视频 AI 任务")
    @SuppressWarnings("unchecked")
    void shouldCreateTaskForCourseOwner() {
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(catalogueClient.queryCatalogueDetail(20L)).thenReturn(section());
        when(mediaClient.queryAiInfo(30L, 10L, 20L)).thenReturn(media());
        when(taskMapper.insert(any(VideoAiTask.class))).thenAnswer(invocation -> {
            VideoAiTask task = invocation.getArgument(0);
            task.setId(1L);
            return 1;
        });

        Long taskId = taskService.createTask(createDTO());

        assertThat(taskId).isEqualTo(1L);
        ArgumentCaptor<VideoAiTask> taskCaptor = ArgumentCaptor.forClass(VideoAiTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(VideoAiTaskStatus.CREATED.name());
        assertThat(taskCaptor.getValue().getDurationMs()).isEqualTo(60_000L);
        assertThat(taskCaptor.getValue().getRetryCount()).isZero();
        ArgumentCaptor<VideoAiTaskRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(VideoAiTaskRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTaskId()).isEqualTo(1L);
    }

    /**
     * 验证文件名没有后缀时能够从媒资地址的 URL path 识别视频格式
     */
    @Test
    @DisplayName("文件名无后缀时应从媒资地址识别视频格式")
    @SuppressWarnings("unchecked")
    void shouldResolveExtensionFromMediaUrlWhenFilenameHasNoExtension() {
        MediaAiInfoDTO media = media();
        media.setFilename("课程视频");
        media.setMediaUrl("https://example.com/video/course-video.MP4?sign=test#fragment");
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(catalogueClient.queryCatalogueDetail(20L)).thenReturn(section());
        when(mediaClient.queryAiInfo(30L, 10L, 20L)).thenReturn(media);
        when(taskMapper.insert(any(VideoAiTask.class))).thenAnswer(invocation -> {
            VideoAiTask task = invocation.getArgument(0);
            task.setId(2L);
            return 1;
        });

        Long taskId = taskService.createTask(createDTO());

        assertThat(taskId).isEqualTo(2L);
        verify(taskMapper).insert(any(VideoAiTask.class));
        verify(eventPublisher).publishEvent(any(VideoAiTaskRequestedEvent.class));
    }

    /**
     * 验证非课程创建者不能创建视频 AI 任务
     */
    @Test
    @DisplayName("非课程创建者创建视频任务时应被拒绝")
    void shouldRejectNonCourseOwner() {
        when(courseClient.baseInfo(10L, true)).thenReturn(course(200L));

        assertThatThrownBy(() -> taskService.createTask(createDTO()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权");

        verify(mediaClient, never()).queryAiInfo(any(), any(), any());
        verify(taskMapper, never()).insert(any(VideoAiTask.class));
    }

    /**
     * 验证相同幂等键命中已有任务时直接返回原任务
     */
    @Test
    @DisplayName("相同幂等请求应返回已有视频任务")
    @SuppressWarnings("unchecked")
    void shouldReturnExistingTaskForIdempotentRequest() {
        VideoAiTask existing = task(VideoAiTaskStatus.COMPLETED).setRequestId("request-1");
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        Long taskId = taskService.createTask(createDTO());

        assertThat(taskId).isEqualTo(1L);
        verify(mediaClient, never()).queryAiInfo(any(), any(), any());
        verify(taskMapper, never()).insert(any(VideoAiTask.class));
    }

    /**
     * 验证失败任务重试会原子增加次数并重新发布执行事件
     */
    @Test
    @DisplayName("失败任务应能在次数限制内重试")
    void shouldRetryFailedTaskWithinLimit() {
        VideoAiTask failed = task(VideoAiTaskStatus.FAILED)
                .setRetryCount(1)
                .setMaxRetryCount(3);
        when(taskMapper.selectById(1L)).thenReturn(failed);
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(mediaClient.queryAiInfo(30L, 10L, 20L)).thenReturn(media());
        when(taskMapper.refreshMediaSnapshot(eq(1L), eq("课程视频.mp4"),
                eq("https://example.com/course.mp4"), eq(1024L), eq(60_000L), any())).thenReturn(1);
        when(taskMapper.resetForRetry(eq(1L), eq(100L), any())).thenReturn(1);

        taskService.retry(1L);

        verify(taskMapper).refreshMediaSnapshot(eq(1L), eq("课程视频.mp4"),
                eq("https://example.com/course.mp4"), eq(1024L), eq(60_000L), any());
        verify(taskMapper).resetForRetry(eq(1L), eq(100L), any());
        ArgumentCaptor<VideoAiTaskRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(VideoAiTaskRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTaskId()).isEqualTo(1L);
    }

    /**
     * 验证达到最大重试次数后不能继续重试
     */
    @Test
    @DisplayName("达到最大重试次数后应拒绝重试")
    void shouldRejectRetryAfterLimitReached() {
        VideoAiTask failed = task(VideoAiTaskStatus.FAILED)
                .setRetryCount(3)
                .setMaxRetryCount(3);
        when(taskMapper.selectById(1L)).thenReturn(failed);
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));

        assertThatThrownBy(() -> taskService.retry(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("最大重试次数");

        verify(taskMapper, never()).resetForRetry(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(VideoAiTaskRequestedEvent.class));
    }

    /**
     * 验证视频分析结果能够映射为小节级测验草稿请求
     */
    @Test
    @DisplayName("完成的视频任务应能创建小节测验草稿")
    void shouldCreateQuizDraftsFromCompletedTask() {
        VideoAiTask completed = task(VideoAiTaskStatus.COMPLETED)
                .setResultVersion("video-ai-v1-1")
                .setVideoIntroduction("视频简介")
                .setCoreContent("核心内容")
                .setKnowledgePoints(List.of(new VideoKnowledgePoint()
                        .setName("循环结构")
                        .setDescription("循环结构基础")
                        .setImportance(5)
                        .setDifficulty(2)
                        .setStartMs(0L)
                        .setEndMs(30_000L)
                        .setPrerequisites(List.of())));
        when(taskMapper.selectById(1L)).thenReturn(completed);
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(catalogueClient.queryCatalogueDetail(20L)).thenReturn(section());
        when(catalogueClient.queryCatalogueDetail(40L)).thenReturn(practice());
        when(examClient.createAiQuestionBatch(any())).thenReturn(99L);

        Long batchId = taskService.createQuizDrafts(1L, quizDTO());

        assertThat(batchId).isEqualTo(99L);
        ArgumentCaptor<AiQuestionBatchCreateRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(AiQuestionBatchCreateRequestDTO.class);
        verify(examClient).createAiQuestionBatch(requestCaptor.capture());
        AiQuestionBatchCreateRequestDTO request = requestCaptor.getValue();
        assertThat(request.getScopeType()).isEqualTo("SECTION");
        assertThat(request.getScopeId()).isEqualTo(20L);
        assertThat(request.getSourceType()).isEqualTo("VIDEO_SUMMARY");
        assertThat(request.getSourceId()).isEqualTo("1");
        assertThat(request.getTargetBizId()).isEqualTo(40L);
        assertThat(request.getKnowledgePoints()).containsExactly("循环结构");
        assertThat(request.getMaterialText()).contains("视频简介", "核心内容", "循环结构");
    }

    /**
     * 验证课程所有者变更后，原任务创建者不能继续创建测验草稿
     */
    @Test
    @DisplayName("课程所有者变更后不允许创建测验草稿")
    void shouldRecheckCurrentCourseOwnerWhenCreatingQuizDrafts() {
        VideoAiTask completed = task(VideoAiTaskStatus.COMPLETED)
                .setResultVersion("video-ai-v1-1")
                .setVideoIntroduction("视频简介")
                .setCoreContent("核心内容");
        when(taskMapper.selectById(1L)).thenReturn(completed);
        when(courseClient.baseInfo(10L, true)).thenReturn(course(200L));

        assertThatThrownBy(() -> taskService.createQuizDrafts(1L, quizDTO()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权对该课程执行视频 AI 处理");

        verify(catalogueClient, never()).queryCatalogueDetail(any());
        verify(examClient, never()).createAiQuestionBatch(any());
    }

    /**
     * 验证多个视频摘要能够按小节顺序聚合为章级综合测试请求
     */
    @Test
    @DisplayName("多个视频任务应能创建章级综合测试草稿")
    void shouldCreateChapterQuizDraftsFromCompletedTasks() {
        VideoAiTask laterTask = chapterTask(1L, 21L, "第二小节视频", "循环结构", "result-1");
        VideoAiTask earlierTask = chapterTask(2L, 20L, "第一小节视频", "变量定义", "result-2");
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectByIds(List.of(1L, 2L))).thenReturn(List.of(laterTask, earlierTask));
        when(catalogueClient.queryCatalogueDetail(5L)).thenReturn(chapter());
        when(catalogueClient.queryCatalogueDetail(40L)).thenReturn(practice());
        when(catalogueClient.queryCatalogueDetails(List.of(21L, 20L)))
                .thenReturn(List.of(chapterSection(20L, "第一小节", 1, 5L),
                        chapterSection(21L, "第二小节", 2, 5L)));
        when(examClient.createAiQuestionBatch(any())).thenReturn(99L, 100L);

        Long firstBatchId = taskService.createChapterQuizDrafts(chapterQuizDTO(List.of(2L, 1L, 2L)));
        Long secondBatchId = taskService.createChapterQuizDrafts(chapterQuizDTO(List.of(1L, 2L)));

        assertThat(firstBatchId).isEqualTo(99L);
        assertThat(secondBatchId).isEqualTo(100L);
        ArgumentCaptor<AiQuestionBatchCreateRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(AiQuestionBatchCreateRequestDTO.class);
        verify(examClient, org.mockito.Mockito.times(2)).createAiQuestionBatch(requestCaptor.capture());
        AiQuestionBatchCreateRequestDTO firstRequest = requestCaptor.getAllValues().get(0);
        AiQuestionBatchCreateRequestDTO secondRequest = requestCaptor.getAllValues().get(1);
        assertThat(firstRequest.getScopeType()).isEqualTo("CHAPTER");
        assertThat(firstRequest.getScopeId()).isEqualTo(5L);
        assertThat(firstRequest.getScopeSectionIds()).containsExactly(20L, 21L);
        assertThat(firstRequest.getSourceType()).isEqualTo("VIDEO_SUMMARY");
        assertThat(firstRequest.getSourceId()).startsWith("chapter-5-videos-");
        assertThat(firstRequest.getSourceVersion()).startsWith("chapter-video-v1-");
        assertThat(firstRequest.getTargetBizId()).isEqualTo(40L);
        assertThat(firstRequest.getKnowledgePoints()).containsExactly("变量定义", "循环结构");
        assertThat(firstRequest.getMaterialText().indexOf("第一小节"))
                .isLessThan(firstRequest.getMaterialText().indexOf("第二小节"));
        assertThat(secondRequest.getRequestId()).isEqualTo(firstRequest.getRequestId());
        assertThat(secondRequest.getSourceId()).isEqualTo(firstRequest.getSourceId());
        assertThat(secondRequest.getSourceVersion()).isEqualTo(firstRequest.getSourceVersion());
    }

    /**
     * 验证未完成的视频任务不能用于章级综合测试
     */
    @Test
    @DisplayName("未完成的视频任务应被拒绝")
    void shouldRejectUncompletedChapterTask() {
        VideoAiTask task = chapterTask(1L, 20L, "第一小节视频", "变量定义", "result-1")
                .setStatus(VideoAiTaskStatus.ANALYZING.name());
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectByIds(List.of(1L))).thenReturn(List.of(task));

        assertThatThrownBy(() -> taskService.createChapterQuizDrafts(chapterQuizDTO(List.of(1L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已完成");

        verify(examClient, never()).createAiQuestionBatch(any());
    }

    /**
     * 验证不能使用其他用户的视频任务
     */
    @Test
    @DisplayName("其他用户的视频任务应被拒绝")
    void shouldRejectChapterTaskOwnedByAnotherUser() {
        VideoAiTask task = chapterTask(1L, 20L, "第一小节视频", "变量定义", "result-1")
                .setCreater(200L);
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectByIds(List.of(1L))).thenReturn(List.of(task));

        assertThatThrownBy(() -> taskService.createChapterQuizDrafts(chapterQuizDTO(List.of(1L))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("其他用户");

        verify(examClient, never()).createAiQuestionBatch(any());
    }

    /**
     * 验证不能聚合其他课程的视频任务
     */
    @Test
    @DisplayName("其他课程的视频任务应被拒绝")
    void shouldRejectChapterTaskFromAnotherCourse() {
        VideoAiTask task = chapterTask(1L, 20L, "第一小节视频", "变量定义", "result-1")
                .setCourseId(11L);
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectByIds(List.of(1L))).thenReturn(List.of(task));

        assertThatThrownBy(() -> taskService.createChapterQuizDrafts(chapterQuizDTO(List.of(1L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("指定课程");

        verify(examClient, never()).createAiQuestionBatch(any());
    }

    /**
     * 验证不能聚合其他章节小节的视频任务
     */
    @Test
    @DisplayName("其他章节小节的视频任务应被拒绝")
    void shouldRejectChapterTaskFromAnotherChapter() {
        VideoAiTask task = chapterTask(1L, 20L, "第一小节视频", "变量定义", "result-1");
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectByIds(List.of(1L))).thenReturn(List.of(task));
        when(catalogueClient.queryCatalogueDetail(5L)).thenReturn(chapter());
        when(catalogueClient.queryCatalogueDetail(40L)).thenReturn(practice());
        when(catalogueClient.queryCatalogueDetails(List.of(20L)))
                .thenReturn(List.of(chapterSection(20L, "第一小节", 1, 6L)));

        assertThatThrownBy(() -> taskService.createChapterQuizDrafts(chapterQuizDTO(List.of(1L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不属于指定章节");

        verify(examClient, never()).createAiQuestionBatch(any());
    }

    /**
     * 验证目标综合测试目录必须挂在指定章节下
     */
    @Test
    @DisplayName("其他章节的综合测试目录应被拒绝")
    void shouldRejectTargetFromAnotherChapter() {
        VideoAiTask task = chapterTask(1L, 20L, "第一小节视频", "变量定义", "result-1");
        CatalogueDetailDTO wrongTarget = practice();
        wrongTarget.setParentCatalogueId(6L);
        when(courseClient.baseInfo(10L, true)).thenReturn(course(100L));
        when(taskMapper.selectByIds(List.of(1L))).thenReturn(List.of(task));
        when(catalogueClient.queryCatalogueDetail(5L)).thenReturn(chapter());
        when(catalogueClient.queryCatalogueDetail(40L)).thenReturn(wrongTarget);

        assertThatThrownBy(() -> taskService.createChapterQuizDrafts(chapterQuizDTO(List.of(1L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("挂在指定章节下");

        verify(catalogueClient, never()).queryCatalogueDetails(any());
        verify(examClient, never()).createAiQuestionBatch(any());
    }

    /**
     * 构造视频 AI 任务创建参数
     *
     * @return 创建参数
     */
    private VideoAiTaskCreateDTO createDTO() {
        VideoAiTaskCreateDTO dto = new VideoAiTaskCreateDTO();
        dto.setCourseId(10L);
        dto.setSectionId(20L);
        dto.setMediaId(30L);
        dto.setRequestId("request-1");
        return dto;
    }

    /**
     * 构造视频测验草稿创建参数
     *
     * @return 测验草稿参数
     */
    private VideoQuizDraftCreateDTO quizDTO() {
        VideoQuizDraftCreateDTO dto = new VideoQuizDraftCreateDTO();
        dto.setTargetBizId(40L);
        dto.setQuestionTypes(List.of(1, 2));
        dto.setQuestionCount(5);
        dto.setDifficulty(2);
        dto.setScore(10);
        return dto;
    }

    /**
     * 构造章级综合测试草稿参数
     *
     * @param taskIds 视频 AI 任务 ID
     * @return 章级综合测试草稿参数
     */
    private ChapterVideoQuizDraftCreateDTO chapterQuizDTO(List<Long> taskIds) {
        ChapterVideoQuizDraftCreateDTO dto = new ChapterVideoQuizDraftCreateDTO();
        dto.setCourseId(10L);
        dto.setChapterId(5L);
        dto.setVideoTaskIds(taskIds);
        dto.setTargetBizId(40L);
        dto.setQuestionTypes(List.of(1, 2));
        dto.setQuestionCount(8);
        dto.setDifficulty(2);
        dto.setScore(10);
        return dto;
    }

    /**
     * 构造用于章级聚合的已完成视频任务
     *
     * @param id 任务 ID
     * @param sectionId 小节 ID
     * @param mediaName 视频名称
     * @param knowledgePoint 知识点名称
     * @param resultVersion 结果版本
     * @return 已完成的视频 AI 任务
     */
    private VideoAiTask chapterTask(Long id, Long sectionId, String mediaName,
                                    String knowledgePoint, String resultVersion) {
        return new VideoAiTask()
                .setId(id)
                .setCourseId(10L)
                .setSectionId(sectionId)
                .setSectionName("小节" + sectionId)
                .setMediaId(30L + id)
                .setMediaName(mediaName)
                .setStatus(VideoAiTaskStatus.COMPLETED.name())
                .setVideoIntroduction(mediaName + "简介")
                .setCoreContent(mediaName + "核心内容")
                .setKnowledgePoints(List.of(new VideoKnowledgePoint()
                        .setName(knowledgePoint)
                        .setDescription(knowledgePoint + "说明")
                        .setStartMs(0L)
                        .setEndMs(30_000L)))
                .setResultVersion(resultVersion)
                .setCreater(100L);
    }

    /**
     * 构造章目录信息
     *
     * @return 章目录
     */
    private CatalogueDetailDTO chapter() {
        CatalogueDetailDTO chapter = new CatalogueDetailDTO();
        chapter.setId(5L);
        chapter.setName("第一章");
        chapter.setCourseId(10L);
        chapter.setType(1);
        return chapter;
    }

    /**
     * 构造章内小节目录信息
     *
     * @param id 小节 ID
     * @param name 小节名称
     * @param index 小节顺序
     * @param chapterId 所属章节 ID
     * @return 小节目录
     */
    private CatalogueDetailDTO chapterSection(Long id, String name, Integer index, Long chapterId) {
        CatalogueDetailDTO section = new CatalogueDetailDTO();
        section.setId(id);
        section.setName(name);
        section.setCourseId(10L);
        section.setType(2);
        section.setParentCatalogueId(chapterId);
        section.setCIndex(index);
        return section;
    }

    /**
     * 构造课程基础信息
     *
     * @param creater 课程创建者
     * @return 课程基础信息
     */
    private CourseBaseInfoDTO course(Long creater) {
        CourseBaseInfoDTO course = new CourseBaseInfoDTO();
        course.setId(10L);
        course.setCreater(creater);
        return course;
    }

    /**
     * 构造视频小节目录信息
     *
     * @return 视频小节目录
     */
    private CatalogueDetailDTO section() {
        CatalogueDetailDTO section = new CatalogueDetailDTO();
        section.setId(20L);
        section.setName("第一小节");
        section.setCourseId(10L);
        section.setType(2);
        section.setParentCatalogueId(5L);
        section.setMediaId(30L);
        return section;
    }

    /**
     * 构造同章练习目录信息
     *
     * @return 练习目录
     */
    private CatalogueDetailDTO practice() {
        CatalogueDetailDTO practice = new CatalogueDetailDTO();
        practice.setId(40L);
        practice.setName("章节测验");
        practice.setCourseId(10L);
        practice.setType(3);
        practice.setParentCatalogueId(5L);
        return practice;
    }

    /**
     * 构造可处理的媒资信息
     *
     * @return 媒资信息
     */
    private MediaAiInfoDTO media() {
        MediaAiInfoDTO media = new MediaAiInfoDTO();
        media.setMediaId(30L);
        media.setFilename("课程视频.mp4");
        media.setMediaUrl("https://example.com/course.mp4");
        media.setDuration(60F);
        media.setSize(1024L);
        media.setStatus(3);
        return media;
    }

    /**
     * 构造指定状态的视频 AI 任务
     *
     * @param status 任务状态
     * @return 视频 AI 任务
     */
    private VideoAiTask task(VideoAiTaskStatus status) {
        return new VideoAiTask()
                .setId(1L)
                .setCourseId(10L)
                .setSectionId(20L)
                .setSectionName("第一小节")
                .setMediaId(30L)
                .setMediaName("课程视频.mp4")
                .setMediaSize(1024L)
                .setDurationMs(60_000L)
                .setStatus(status.name())
                .setRetryCount(0)
                .setMaxRetryCount(3)
                .setCreater(100L);
    }
}