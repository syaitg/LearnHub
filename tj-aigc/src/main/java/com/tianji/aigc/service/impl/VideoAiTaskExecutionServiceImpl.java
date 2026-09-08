package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.model.TranscriptSegment;
import com.tianji.aigc.domain.model.VideoContentAnalysisResult;
import com.tianji.aigc.domain.model.VideoTranscriptionRequest;
import com.tianji.aigc.domain.model.VideoTranscriptionResult;
import com.tianji.aigc.domain.po.VideoAiTask;
import com.tianji.aigc.enums.VideoAiTaskStatus;
import com.tianji.aigc.mapper.VideoAiTaskMapper;
import com.tianji.aigc.service.VideoAiTaskExecutionService;
import com.tianji.aigc.service.VideoContentAnalysisService;
import com.tianji.aigc.service.VideoTranscriptionProvider;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频 AI 异步任务执行服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAiTaskExecutionServiceImpl implements VideoAiTaskExecutionService {

    private static final int FAILURE_REASON_MAX_LENGTH = 1000;

    private final VideoAiTaskMapper taskMapper;
    private final List<VideoTranscriptionProvider> transcriptionProviders;
    private final VideoContentAnalysisService contentAnalysisService;
    private final VideoAiProperties properties;

    /**
     * 在异步任务边界执行转写与内容分析，并统一记录失败状态
     *
     * @param taskId 任务 ID
     */
    @Override
    public void execute(Long taskId) {
        VideoAiTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        int expectedRetryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        UserContext.setUser(task.getCreater());
        try {
            task = executeTranscriptionIfNecessary(task, expectedRetryCount);
            executeAnalysisIfNecessary(task, expectedRetryCount);
        } catch (Exception e) {
            log.error("视频 AI 任务 {} 执行失败", taskId, e);
            taskMapper.markFailed(taskId, expectedRetryCount, normalizeFailureReason(e), LocalDateTime.now());
        } finally {
            UserContext.removeUser();
        }
    }

    /**
     * 在任务尚未转写时执行转写，并返回最新任务状态
     *
     * @param task 当前任务
     * @param expectedRetryCount 本次执行对应的重试次数
     * @return 最新任务
     */
    private VideoAiTask executeTranscriptionIfNecessary(VideoAiTask task, int expectedRetryCount) {
        if (!VideoAiTaskStatus.CREATED.name().equals(task.getStatus())) {
            return task;
        }
        if (taskMapper.startTranscription(task.getId(), expectedRetryCount, LocalDateTime.now()) != 1) {
            return taskMapper.selectById(task.getId());
        }
        VideoTranscriptionProvider provider = selectProvider();
        VideoTranscriptionResult result = provider.transcribe(toTranscriptionRequest(task));
        validateTranscriptionResult(result, task.getDurationMs());
        int updated = taskMapper.saveTranscription(
                task.getId(),
                result.getProvider(),
                result.getProviderVersion(),
                result.getLanguage(),
                result.getFullText(),
                JsonUtils.toJsonStr(result.getSegments()),
                result.getTimestampMode(),
                expectedRetryCount,
                LocalDateTime.now());
        VideoAiTask latest = taskMapper.selectById(task.getId());
        if (updated == 0) {
            log.warn("视频 AI 任务 {} 的转写结果未保存，当前状态为 {}", task.getId(),
                    latest == null ? "任务不存在" : latest.getStatus());
        }
        return latest;
    }

    /**
     * 在任务已经完成转写时执行摘要与知识点分析
     *
     * @param task 当前任务
     * @param expectedRetryCount 本次执行对应的重试次数
     */
    private void executeAnalysisIfNecessary(VideoAiTask task, int expectedRetryCount) {
        if (task == null || !VideoAiTaskStatus.TRANSCRIBED.name().equals(task.getStatus())) {
            return;
        }
        if (taskMapper.startAnalysis(task.getId(), expectedRetryCount, LocalDateTime.now()) != 1) {
            return;
        }
        VideoAiTask analyzingTask = taskMapper.selectById(task.getId());
        if (analyzingTask == null || !VideoAiTaskStatus.ANALYZING.name().equals(analyzingTask.getStatus())) {
            return;
        }
        VideoContentAnalysisResult result = contentAnalysisService.analyze(analyzingTask);
        String resultVersion = buildResultVersion();
        int updated = taskMapper.completeAnalysis(
                task.getId(),
                result.getIntroduction(),
                result.getCoreContent(),
                JsonUtils.toJsonStr(result.getSectionSummaries()),
                JsonUtils.toJsonStr(result.getKeyConclusions()),
                JsonUtils.toJsonStr(result.getSuitableLearners()),
                JsonUtils.toJsonStr(result.getReviewPoints()),
                JsonUtils.toJsonStr(result.getKnowledgePoints()),
                resultVersion,
                expectedRetryCount,
                LocalDateTime.now());
        if (updated == 0) {
            VideoAiTask latest = taskMapper.selectById(task.getId());
            log.warn("视频 AI 任务 {} 的分析结果未保存，当前状态为 {}", task.getId(),
                    latest == null ? "任务不存在" : latest.getStatus());
        }
    }

    /**
     * 根据配置选择视频转写 Provider
     *
     * @return 匹配的转写 Provider
     */
    private VideoTranscriptionProvider selectProvider() {
        return transcriptionProviders.stream()
                .filter(provider -> provider.providerName().equalsIgnoreCase(properties.getProvider()))
                .findFirst()
                .orElseThrow(() -> new BizIllegalException("未找到视频转写 Provider：" + properties.getProvider()));
    }

    /**
     * 组装统一的视频转写请求
     *
     * @param task 视频 AI 任务
     * @return 转写请求
     */
    private VideoTranscriptionRequest toTranscriptionRequest(VideoAiTask task) {
        return new VideoTranscriptionRequest()
                .setTaskId(task.getId())
                .setMediaId(task.getMediaId())
                .setFilename(task.getMediaName())
                .setMediaUrl(task.getMediaUrl())
                .setSize(task.getMediaSize())
                .setDurationMs(task.getDurationMs())
                .setLanguage(task.getLanguage());
    }

    /**
     * 校验 Provider 返回的转写结果
     *
     * @param result 转写结果
     * @param durationMs 视频时长
     */
    private void validateTranscriptionResult(VideoTranscriptionResult result, Long durationMs) {
        if (result == null || result.getFullText() == null || result.getFullText().isBlank()) {
            throw new BizIllegalException("视频转写结果为空");
        }
        if (result.getFullText().length() > properties.getTranscriptionMaxChars()) {
            throw new BizIllegalException("视频转写全文超过允许的最大长度");
        }
        if (result.getSegments() == null || result.getSegments().isEmpty()) {
            throw new BizIllegalException("视频转写结果缺少时间戳句段");
        }
        if (result.getSegments().size() > properties.getTranscriptMaxSegments()) {
            throw new BizIllegalException("视频转写句段数量超过允许的最大数量");
        }
        if (result.getProvider() == null || result.getProvider().isBlank()
                || result.getProvider().length() > 64
                || result.getProviderVersion() == null || result.getProviderVersion().isBlank()
                || result.getProviderVersion().length() > 64
                || result.getLanguage() != null && result.getLanguage().length() > 16
                || result.getTimestampMode() == null || result.getTimestampMode().isBlank()
                || result.getTimestampMode().length() > 32) {
            throw new BizIllegalException("视频转写结果 Provider 元数据长度或内容无效");
        }
        for (TranscriptSegment segment : result.getSegments()) {
            validateSegment(segment, durationMs);
        }
    }

    /**
     * 校验单个转写句段
     *
     * @param segment 转写句段
     * @param durationMs 视频时长
     */
    private void validateSegment(TranscriptSegment segment, Long durationMs) {
        if (segment == null || segment.getText() == null || segment.getText().isBlank()
                || segment.getText().length() > properties.getTranscriptSegmentMaxChars()
                || segment.getStartMs() == null || segment.getEndMs() == null
                || segment.getStartMs() < 0 || segment.getEndMs() < segment.getStartMs()
                || durationMs == null || segment.getEndMs() > durationMs) {
            throw new BizIllegalException("视频转写句段格式无效");
        }
    }

    /**
     * 构造长度稳定的视频分析结果版本。
     *
     * @return 不超过数据库字段长度的结果版本
     */
    private String buildResultVersion() {
        String configVersion = properties.getConfigVersion();
        String suffix = "-" + System.currentTimeMillis();
        int prefixMaxLength = 64 - suffix.length();
        if (configVersion == null || configVersion.isBlank()) {
            configVersion = "video-ai";
        }
        if (configVersion.length() > prefixMaxLength) {
            configVersion = configVersion.substring(0, prefixMaxLength);
        }
        return configVersion + suffix;
    }
    /**
     * 规范化并截断异步任务失败原因
     *
     * @param exception 任务异常
     * @return 可持久化的失败原因
     */
    private String normalizeFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "视频 AI 任务执行失败";
        }
        String normalized = message.trim();
        return normalized.length() <= FAILURE_REASON_MAX_LENGTH
                ? normalized
                : normalized.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
