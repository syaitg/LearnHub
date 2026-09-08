package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.model.TranscriptSegment;
import com.tianji.aigc.domain.model.VideoContentAnalysisResult;
import com.tianji.aigc.domain.model.VideoTranscriptionResult;
import com.tianji.aigc.domain.po.VideoAiTask;
import com.tianji.aigc.enums.VideoAiTaskStatus;
import com.tianji.aigc.mapper.VideoAiTaskMapper;
import com.tianji.aigc.service.VideoContentAnalysisService;
import com.tianji.aigc.service.VideoTranscriptionProvider;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频 AI 异步任务执行服务测试
 */
@ExtendWith(MockitoExtension.class)
class VideoAiTaskExecutionServiceImplTest {

    @Mock
    private VideoAiTaskMapper taskMapper;
    @Mock
    private VideoTranscriptionProvider transcriptionProvider;
    @Mock
    private VideoContentAnalysisService contentAnalysisService;

    private VideoAiTaskExecutionServiceImpl executionService;
    private VideoAiProperties properties;

    /**
     * 初始化视频 AI 任务执行服务
     */
    @BeforeEach
    void setUp() {
        properties = new VideoAiProperties();
        properties.setProvider("audio-service");
        properties.setConfigVersion("video-ai-v1");
        executionService = new VideoAiTaskExecutionServiceImpl(
                taskMapper, List.of(transcriptionProvider), contentAnalysisService, properties);
    }

    /**
     * 清理测试线程中的用户上下文
     */
    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /**
     * 验证创建状态的任务能够依次完成转写和内容分析
     */
    @Test
    @DisplayName("视频任务应依次完成转写和内容分析")
    void shouldCompleteTranscriptionAndAnalysis() {
        when(transcriptionProvider.providerName()).thenReturn("audio-service");
        VideoAiTask created = task(VideoAiTaskStatus.CREATED);
        VideoAiTask transcribed = task(VideoAiTaskStatus.TRANSCRIBED).setFullText("课程转写内容");
        VideoAiTask analyzing = task(VideoAiTaskStatus.ANALYZING).setFullText("课程转写内容");
        when(taskMapper.selectById(1L)).thenReturn(created, transcribed, analyzing);
        when(taskMapper.startTranscription(eq(1L), eq(0), any())).thenReturn(1);
        when(transcriptionProvider.transcribe(any())).thenReturn(transcriptionResult());
        when(taskMapper.saveTranscription(eq(1L), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(0), any())).thenReturn(1);
        when(taskMapper.startAnalysis(eq(1L), eq(0), any())).thenReturn(1);
        when(contentAnalysisService.analyze(analyzing)).thenReturn(analysisResult());
        when(taskMapper.completeAnalysis(eq(1L), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), eq(0), any())).thenReturn(1);

        executionService.execute(1L);

        verify(transcriptionProvider).transcribe(any());
        verify(contentAnalysisService).analyze(analyzing);
        verify(taskMapper).completeAnalysis(eq(1L), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), eq(0), any());
        verify(taskMapper, never()).markFailed(eq(1L), eq(0), anyString(), any());
        assertThat(UserContext.getUser()).isNull();
    }

    /**
     * 验证并发执行器未领取到转写任务时不会重复调用转写服务
     */
    @Test
    @DisplayName("未领取到转写任务时不应重复执行")
    void shouldNotExecuteWhenTranscriptionWasClaimed() {
        VideoAiTask created = task(VideoAiTaskStatus.CREATED);
        VideoAiTask transcribing = task(VideoAiTaskStatus.TRANSCRIBING);
        when(taskMapper.selectById(1L)).thenReturn(created, transcribing);
        when(taskMapper.startTranscription(eq(1L), eq(0), any())).thenReturn(0);

        executionService.execute(1L);

        verify(transcriptionProvider, never()).transcribe(any());
        verify(contentAnalysisService, never()).analyze(any());
        verify(taskMapper, never()).markFailed(eq(1L), eq(0), anyString(), any());
    }

    /**
     * 验证超时扫描已将任务置为失败时不会用迟到的转写结果覆盖失败状态
     */
    @Test
    @DisplayName("迟到的转写结果不应覆盖失败状态")
    void shouldIgnoreLateTranscriptionResult() {
        when(transcriptionProvider.providerName()).thenReturn("audio-service");
        VideoAiTask created = task(VideoAiTaskStatus.CREATED);
        VideoAiTask failed = task(VideoAiTaskStatus.FAILED);
        when(taskMapper.selectById(1L)).thenReturn(created, failed);
        when(taskMapper.startTranscription(eq(1L), eq(0), any())).thenReturn(1);
        when(transcriptionProvider.transcribe(any())).thenReturn(transcriptionResult());
        when(taskMapper.saveTranscription(eq(1L), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(0), any())).thenReturn(0);

        executionService.execute(1L);

        verify(contentAnalysisService, never()).analyze(any());
        verify(taskMapper, never()).markFailed(eq(1L), eq(0), anyString(), any());
    }

    /**
     * 验证内容分析异常会在异步任务边界回写失败状态
     */
    @Test
    @DisplayName("内容分析异常时应回写失败状态")
    void shouldMarkFailedWhenAnalysisThrowsException() {
        VideoAiTask transcribed = task(VideoAiTaskStatus.TRANSCRIBED).setRetryCount(2).setFullText("课程转写内容");
        VideoAiTask analyzing = task(VideoAiTaskStatus.ANALYZING).setRetryCount(2).setFullText("课程转写内容");
        when(taskMapper.selectById(1L)).thenReturn(transcribed, analyzing);
        when(taskMapper.startAnalysis(eq(1L), eq(2), any())).thenReturn(1);
        when(contentAnalysisService.analyze(analyzing)).thenThrow(new IllegalStateException("模型分析失败"));
        when(taskMapper.markFailed(eq(1L), eq(2), eq("模型分析失败"), any())).thenReturn(1);

        executionService.execute(1L);

        verify(taskMapper).markFailed(eq(1L), eq(2), eq("模型分析失败"), any());
        verify(taskMapper, never()).completeAnalysis(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), eq(2), any());
    }

    /**
     * 验证迟到的分析结果不会覆盖超时扫描写入的失败状态
     */
    @Test
    @DisplayName("迟到的分析结果不应覆盖失败状态")
    void shouldIgnoreLateAnalysisResult() {
        VideoAiTask transcribed = task(VideoAiTaskStatus.TRANSCRIBED).setFullText("课程转写内容");
        VideoAiTask analyzing = task(VideoAiTaskStatus.ANALYZING).setFullText("课程转写内容");
        VideoAiTask failed = task(VideoAiTaskStatus.FAILED).setFullText("课程转写内容");
        when(taskMapper.selectById(1L)).thenReturn(transcribed, analyzing, failed);
        when(taskMapper.startAnalysis(eq(1L), eq(0), any())).thenReturn(1);
        when(contentAnalysisService.analyze(analyzing)).thenReturn(analysisResult());
        when(taskMapper.completeAnalysis(eq(1L), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), eq(0), any())).thenReturn(0);

        executionService.execute(1L);

        verify(taskMapper, never()).markFailed(eq(1L), eq(0), anyString(), any());
    }

    /**
     * 验证异常大的转写结果会被拒绝，避免异常数据撑爆数据库和后续模型调用。
     */
    @Test
    @DisplayName("超大转写结果应被拒绝")
    void shouldRejectOversizedTranscriptionResult() {
        VideoAiTask created = task(VideoAiTaskStatus.CREATED);
        when(transcriptionProvider.providerName()).thenReturn("audio-service");
        when(taskMapper.selectById(1L)).thenReturn(created);
        when(taskMapper.startTranscription(eq(1L), eq(0), any())).thenReturn(1);
        when(transcriptionProvider.transcribe(any())).thenReturn(transcriptionResult()
                .setFullText("超长".repeat(properties.getTranscriptionMaxChars() + 1)));
        when(taskMapper.markFailed(eq(1L), eq(0), eq("视频转写全文超过允许的最大长度"), any())).thenReturn(1);

        executionService.execute(1L);

        verify(taskMapper).markFailed(eq(1L), eq(0), eq("视频转写全文超过允许的最大长度"), any());
        verify(taskMapper, never()).saveTranscription(any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(0), any());
    }

    /**
     * 验证结果版本即使遇到异常长的配置版本也不会超过数据库字段长度。
     */
    @Test
    @DisplayName("结果版本长度应稳定")
    void shouldKeepResultVersionWithinColumnLength() {
        properties.setConfigVersion("配置版本".repeat(30));
        VideoAiTask transcribed = task(VideoAiTaskStatus.TRANSCRIBED).setFullText("课程转写内容");
        VideoAiTask analyzing = task(VideoAiTaskStatus.ANALYZING).setFullText("课程转写内容");
        when(taskMapper.selectById(1L)).thenReturn(transcribed, analyzing);
        when(taskMapper.startAnalysis(eq(1L), eq(0), any())).thenReturn(1);
        when(contentAnalysisService.analyze(analyzing)).thenReturn(analysisResult());
        when(taskMapper.completeAnalysis(eq(1L), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), eq(0), any())).thenReturn(1);

        executionService.execute(1L);

        ArgumentCaptor<String> versionCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskMapper).completeAnalysis(eq(1L), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), versionCaptor.capture(), eq(0), any());
        assertThat(versionCaptor.getValue()).hasSizeLessThanOrEqualTo(64);
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
                .setMediaUrl("https://example.com/course.mp4")
                .setMediaSize(1024L)
                .setDurationMs(60_000L)
                .setLanguage("zh")
                .setStatus(status.name())
                .setRetryCount(0)
                .setCreater(100L);
    }

    /**
     * 构造合法的视频转写结果
     *
     * @return 视频转写结果
     */
    private VideoTranscriptionResult transcriptionResult() {
        return new VideoTranscriptionResult()
                .setProvider("audio-service")
                .setProviderVersion("v1")
                .setLanguage("zh")
                .setFullText("课程转写内容。")
                .setTimestampMode("ESTIMATED")
                .setSegments(List.of(new TranscriptSegment()
                        .setStartMs(0L)
                        .setEndMs(60_000L)
                        .setText("课程转写内容。")));
    }

    /**
     * 构造合法的视频内容分析结果
     *
     * @return 视频内容分析结果
     */
    private VideoContentAnalysisResult analysisResult() {
        return new VideoContentAnalysisResult()
                .setIntroduction("视频简介")
                .setCoreContent("核心内容")
                .setSectionSummaries(new ArrayList<>())
                .setKeyConclusions(new ArrayList<>())
                .setSuitableLearners(new ArrayList<>())
                .setReviewPoints(new ArrayList<>())
                .setKnowledgePoints(new ArrayList<>());
    }
}
