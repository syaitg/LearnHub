package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.model.TranscriptSegment;
import com.tianji.aigc.domain.model.VideoTranscriptionRequest;
import com.tianji.aigc.service.AudioService;
import com.tianji.common.exceptions.BizIllegalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 视频转写媒资下载安全校验测试
 */
class AudioServiceVideoTranscriptionProviderTest {

    private AudioService audioService;
    private AudioServiceVideoTranscriptionProvider provider;

    /**
     * 初始化视频转写 Provider 和默认配置
     */
    @BeforeEach
    void setUp() {
        audioService = mock(AudioService.class);
        VideoAiProperties properties = new VideoAiProperties();
        properties.setMaxSizeBytes(1024L);
        properties.setRequestTimeout(Duration.ofSeconds(2));
        provider = new AudioServiceVideoTranscriptionProvider(audioService, properties);
    }

    /**
     * 验证非 HTTP 协议的媒资地址不会被服务端访问
     */
    @Test
    @DisplayName("非 HTTP 协议媒资地址应被拒绝")
    void shouldRejectNonHttpMediaUrl() {
        assertThatThrownBy(() -> provider.transcribe(request("file:///tmp/course.mp4")))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("HTTP 或 HTTPS");

        verify(audioService, never()).stt(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 验证回环地址不会被用于服务端请求
     */
    @Test
    @DisplayName("回环媒资地址应被拒绝")
    void shouldRejectLoopbackMediaUrl() {
        assertThatThrownBy(() -> provider.transcribe(request("http://127.0.0.1/private.mp4")))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("本机或内网地址");

        verify(audioService, never()).stt(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 验证内网地址不会被用于服务端请求
     */
    @Test
    @DisplayName("内网媒资地址应被拒绝")
    void shouldRejectPrivateMediaUrl() {
        assertThatThrownBy(() -> provider.transcribe(request("http://10.0.0.8/private.mp4")))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("本机或内网地址");

        verify(audioService, never()).stt(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 验证携带用户信息的媒资地址不会被接受
     */
    @Test
    @DisplayName("携带用户信息的媒资地址应被拒绝")
    void shouldRejectMediaUrlWithUserInfo() {
        assertThatThrownBy(() -> provider.transcribe(request("https://user:password@example.com/course.mp4")))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("合法的 HTTP 或 HTTPS 地址");

        verify(audioService, never()).stt(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 验证极短视频包含多个句段时，估算时间戳仍然位于视频时长范围内
     */
    @Test
    @DisplayName("极短视频的估算时间戳不得超出视频时长")
    void shouldKeepEstimatedSegmentsWithinDurationForShortVideo() throws Exception {
        Method method = AudioServiceVideoTranscriptionProvider.class
                .getDeclaredMethod("estimateSegments", String.class, Long.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<TranscriptSegment> segments = (List<TranscriptSegment>) method.invoke(
                provider, "第一句。第二句。第三句。", 1L);

        assertThat(segments).isNotEmpty().allSatisfy(segment -> {
            assertThat(segment.getStartMs()).isBetween(0L, 1L);
            assertThat(segment.getEndMs()).isBetween(0L, 1L);
            assertThat(segment.getEndMs()).isGreaterThanOrEqualTo(segment.getStartMs());
        });
        assertThat(segments.get(segments.size() - 1).getEndMs()).isEqualTo(1L);
    }

    /**
     * 验证文件名无后缀时，临时文件仍能依据媒资地址保留视频格式。
     */
    @Test
    @DisplayName("文件名无后缀时临时文件应从媒资地址保留视频扩展名")
    void shouldResolveTemporaryFileExtensionFromMediaUrl() throws Exception {
        Method method = AudioServiceVideoTranscriptionProvider.class
                .getDeclaredMethod("createTemporaryFile", String.class, String.class);
        method.setAccessible(true);

        Path temporaryFile = (Path) method.invoke(provider, "课程视频",
                "https://example.com/media/course-video.MP4?sign=abc#fragment");
        try {
            assertThat(temporaryFile.getFileName().toString()).endsWith(".mp4");
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * 验证 URL path 没有扩展名时不会把域名中的点号误识别为视频格式。
     */
    @Test
    @DisplayName("媒资地址无文件扩展名时不应误判域名后缀")
    void shouldNotResolveExtensionFromDomainName() throws Exception {
        Method method = AudioServiceVideoTranscriptionProvider.class
                .getDeclaredMethod("createTemporaryFile", String.class, String.class);
        method.setAccessible(true);

        Path temporaryFile = (Path) method.invoke(provider, "课程视频", "https://video.example.com/media/course-video");
        try {
            assertThat(temporaryFile.getFileName().toString()).endsWith(".media");
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * 验证交给语音识别服务的原始文件名会补齐从媒资地址识别出的扩展名。
     */
    @Test
    @DisplayName("无后缀文件名提交转写服务时应补齐视频扩展名")
    void shouldAddExtensionToUploadFilename() throws Exception {
        Method method = AudioServiceVideoTranscriptionProvider.class
                .getDeclaredMethod("resolveUploadFilename", String.class, String.class, Path.class);
        method.setAccessible(true);

        String uploadFilename = (String) method.invoke(provider, "课程视频",
                "https://example.com/media/course-video.MP4?sign=abc", Path.of("video-ai-123.mp4"));

        assertThat(uploadFilename).isEqualTo("课程视频.mp4");
    }

    /**
     * 验证语音模型控制标记会被清理，且不会破坏正文分隔。
     */
    @Test
    @DisplayName("语音模型控制标记不应出现在转写结果中")
    void shouldRemoveModelControlMarkersFromTranscript() {
        String normalized = AudioServiceVideoTranscriptionProvider.normalizeTranscriptText(
                "<|Speech|>第一句。<|NEUTRAL|>\n<|/Speech|>第二句。");

        assertThat(normalized).isEqualTo("第一句。 第二句。");
        assertThat(AudioServiceVideoTranscriptionProvider.normalizeTranscriptText(
                "<|Speech|><|/Speech|><|NEUTRAL|>")).isEmpty();
    }

    private VideoTranscriptionRequest request(String mediaUrl) {
        return new VideoTranscriptionRequest()
                .setTaskId(1L)
                .setMediaId(2L)
                .setFilename("course.mp4")
                .setMediaUrl(mediaUrl)
                .setDurationMs(60_000L)
                .setLanguage("zh");
    }
}
