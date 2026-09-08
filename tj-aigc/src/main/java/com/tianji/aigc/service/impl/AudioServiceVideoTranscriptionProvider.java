package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.model.TranscriptSegment;
import com.tianji.aigc.domain.model.VideoTranscriptionRequest;
import com.tianji.aigc.domain.model.VideoTranscriptionResult;
import com.tianji.aigc.service.AudioService;
import com.tianji.aigc.service.VideoTranscriptionProvider;
import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 基于项目现有语音识别服务的视频转写 Provider
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioServiceVideoTranscriptionProvider implements VideoTranscriptionProvider {

    private static final String PROVIDER_NAME = "audio-service";
    private static final String TIMESTAMP_MODE_ESTIMATED = "ESTIMATED";
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECT_COUNT = 3;
    private static final java.util.regex.Pattern MODEL_CONTROL_MARKER_PATTERN =
            java.util.regex.Pattern.compile("<\\|[^\\r\\n|]{1,64}\\|>");

    private final AudioService audioService;
    private final VideoAiProperties properties;

    /**
     * 获取当前 Provider 标识
     *
     * @return Provider 标识
     */
    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    /**
     * 下载媒资并调用现有语音识别能力完成转写
     *
     * @param request 转写请求
     * @return 统一转写结果
     */
    @Override
    public VideoTranscriptionResult transcribe(VideoTranscriptionRequest request) {
        Path temporaryFile = null;
        try {
            temporaryFile = createTemporaryFile(request.getFilename(), request.getMediaUrl());
            downloadMedia(request.getMediaUrl(), temporaryFile, properties.getMaxSizeBytes());
            // 媒资服务返回的是带有效期的防盗链地址。直接交给语音服务，避免依赖未配置的 OSS
            // 临时上传链路；本地下载仍保留，用于校验地址可访问性和文件大小限制。
            String fullText = audioService.sttUrl(request.getMediaUrl());
            if (fullText == null || fullText.isBlank()) {
                throw new BizIllegalException("视频转写服务未返回有效文本");
            }
            String normalizedText = normalizeTranscriptText(fullText);
            if (normalizedText.isEmpty()) {
                throw new BizIllegalException("视频转写服务未返回有效文本");
            }
            return new VideoTranscriptionResult()
                    .setLanguage(request.getLanguage())
                    .setFullText(normalizedText)
                    .setSegments(estimateSegments(normalizedText, request.getDurationMs()))
                    .setProvider(PROVIDER_NAME)
                    .setProviderVersion(properties.getProviderVersion())
                    .setTimestampMode(TIMESTAMP_MODE_ESTIMATED);
        } catch (IOException e) {
            throw new BizIllegalException(500, "下载或读取待转写媒资失败", e);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    /**
     * 创建保留原文件扩展名的临时媒资文件
     *
     * @param filename 原始文件名
     * @param mediaUrl 媒资访问地址
     * @return 临时文件路径
     * @throws IOException 创建失败时抛出
     */
    private Path createTemporaryFile(String filename, String mediaUrl) throws IOException {
        String extension = resolveMediaExtension(filename, mediaUrl);
        return Files.createTempFile("video-ai-", extension.isEmpty() ? ".media" : "." + extension);
    }

    /**
     * 下载媒体文件并在写入过程中执行大小限制
     *
     * @param mediaUrl 媒体地址
     * @param target 目标文件
     * @param maxSizeBytes 最大允许字节数
     * @throws IOException 下载或写入失败时抛出
     */
    private void downloadMedia(String mediaUrl, Path target, long maxSizeBytes) throws IOException {
        URI currentUri = parseAndValidateUri(mediaUrl);
        int timeoutMillis = resolveTimeoutMillis(properties.getRequestTimeout());
        if (maxSizeBytes <= 0) {
            throw new BizIllegalException("视频 AI 媒资大小限制配置无效");
        }

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECT_COUNT; redirectCount++) {
            HttpURLConnection connection = openConnection(currentUri, timeoutMillis);
            try {
                int statusCode = connection.getResponseCode();
                if (statusCode >= 300 && statusCode < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new BizIllegalException("下载待转写媒资失败：重定向地址为空");
                    }
                    if (redirectCount == MAX_REDIRECT_COUNT) {
                        throw new BizIllegalException("下载待转写媒资失败：重定向次数超过限制");
                    }
                    URI redirectUri = parseAndValidateUri(currentUri.resolve(location).toString());
                    if ("https".equalsIgnoreCase(currentUri.getScheme())
                            && "http".equalsIgnoreCase(redirectUri.getScheme())) {
                        throw new BizIllegalException("下载待转写媒资失败：不允许从 HTTPS 降级到 HTTP");
                    }
                    currentUri = redirectUri;
                    continue;
                }
                if (statusCode < 200 || statusCode >= 300) {
                    throw new BizIllegalException("下载待转写媒资失败，响应状态码：" + statusCode);
                }

                long contentLength = connection.getContentLengthLong();
                if (contentLength > maxSizeBytes) {
                    throw new BizIllegalException("待转写媒资超过视频 AI 处理大小限制");
                }
                try (InputStream input = connection.getInputStream();
                     OutputStream output = Files.newOutputStream(target)) {
                    copyWithLimit(input, output, maxSizeBytes);
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new BizIllegalException("下载待转写媒资失败");
    }

    /**
     * 创建禁止自动重定向且带超时限制的 HTTP 连接
     *
     * @param uri 媒资 URI
     * @param timeoutMillis 连接和读取超时毫秒数
     * @return HTTP 连接
     * @throws IOException 连接创建失败时抛出
     */
    private HttpURLConnection openConnection(URI uri, int timeoutMillis) throws IOException {
        URLConnection rawConnection = uri.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection connection)) {
            throw new BizIllegalException("媒资地址仅支持 HTTP 或 HTTPS 协议");
        }
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        return connection;
    }

    /**
     * 将超时配置转换为 URLConnection 可使用的毫秒数
     *
     * @param timeout 请求超时时间
     * @return 正整数毫秒值
     */
    private int resolveTimeoutMillis(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new BizIllegalException("视频 AI 请求超时时间配置无效");
        }
        return (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
    }

    /**
     * 解析并校验媒资地址，避免非 HTTP 地址及内网地址被服务端主动访问
     *
     * @param mediaUrl 原始媒资地址
     * @return 校验后的 URI
     */
    private URI parseAndValidateUri(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new BizIllegalException("媒资地址不能为空");
        }
        final URI uri;
        try {
            uri = URI.create(mediaUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new BizIllegalException("媒资地址格式不合法");
        }
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw new BizIllegalException("媒资地址仅支持合法的 HTTP 或 HTTPS 地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new BizIllegalException("媒资地址不能指向本机或内网地址");
                }
            }
        } catch (UnknownHostException e) {
            throw new BizIllegalException("媒资地址域名无法解析");
        }
        return uri;
    }

    /**
     * 在限制范围内复制媒体数据
     *
     * @param input 输入流
     * @param output 输出流
     * @param maxSizeBytes 最大允许字节数
     * @throws IOException 读取或写入失败时抛出
     */
    private void copyWithLimit(InputStream input, OutputStream output, long maxSizeBytes) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        int length;
        while ((length = input.read(buffer)) != -1) {
            total += length;
            if (total > maxSizeBytes) {
                throw new BizIllegalException("下载的媒资文件超过视频 AI 处理大小限制");
            }
            output.write(buffer, 0, length);
        }
    }

    /**
     * 清理语音模型输出中的控制标记，避免把内部标记保存到用户可见的转写结果。
     *
     * @param text 模型原始输出
     * @return 可读的转写文本
     */
    static String normalizeTranscriptText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String withoutControlMarkers = MODEL_CONTROL_MARKER_PATTERN.matcher(text).replaceAll("");
        return withoutControlMarkers.replaceAll("\\s+", " ").trim();
    }

    /**
     * 根据中文句子长度估算句段时间戳
     *
     * @param fullText 转写全文
     * @param durationMs 视频时长
     * @return 带估算时间戳的句段列表
     */
    private List<TranscriptSegment> estimateSegments(String fullText, Long durationMs) {
        String[] sentences = fullText.split("(?<=[。！？!?；;])\\s*");
        List<String> texts = new ArrayList<>();
        int totalCharacters = 0;
        for (String sentence : sentences) {
            String text = sentence == null ? "" : sentence.trim();
            if (!text.isEmpty()) {
                texts.add(text);
                totalCharacters += text.length();
            }
        }
        if (texts.isEmpty()) {
            texts.add(fullText);
            totalCharacters = fullText.length();
        }
        long totalDuration = durationMs == null || durationMs <= 0 ? texts.size() * 1000L : durationMs;
        List<TranscriptSegment> segments = new ArrayList<>(texts.size());
        long start = 0L;
        int consumedCharacters = 0;
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            consumedCharacters += text.length();
            long proportionalEnd = totalDuration * consumedCharacters / Math.max(totalCharacters, 1);
            long end = index == texts.size() - 1
                    ? totalDuration
                    : Math.min(totalDuration, Math.max(start, proportionalEnd));
            segments.add(new TranscriptSegment().setStartMs(start).setEndMs(end).setText(text));
            start = end;
        }
        return segments;
    }

    /**
     * 提取文件扩展名
     *
     * @param filename 文件名
     * @return 小写扩展名
     */
    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String normalized = filename.trim();
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= slashIndex || dotIndex == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 解析媒资文件扩展名。
     * 文件名没有后缀时，从媒资地址的 URL path 中提取扩展名，查询参数、片段标识和域名不会参与判断。
     *
     * @param filename 原始文件名
     * @param mediaUrl 媒资访问地址
     * @return 小写文件扩展名
     */
    private String resolveMediaExtension(String filename, String mediaUrl) {
        String extension = extractExtension(filename);
        if (!extension.isBlank() || mediaUrl == null || mediaUrl.isBlank()) {
            return extension;
        }
        try {
            URI uri = URI.create(mediaUrl.trim());
            return extractExtension(uri.getPath());
        } catch (IllegalArgumentException e) {
            // 媒资地址格式异常时返回空扩展名，继续由统一的格式校验返回业务提示。
            return "";
        }
    }

    /**
     * 生成交给语音识别服务的文件名。
     * 部分语音识别服务会依据 MultipartFile 的原始文件名判断媒体格式，因此无后缀文件名必须补上已解析的扩展名。
     *
     * @param filename 原始文件名
     * @param mediaUrl 媒资访问地址
     * @param temporaryFile 临时文件
     * @return 带有可靠扩展名的文件名
     */
    private String resolveUploadFilename(String filename, String mediaUrl, Path temporaryFile) {
        String normalizedFilename = filename == null || filename.isBlank()
                ? "video-ai-media"
                : filename.trim();
        if (!extractExtension(normalizedFilename).isBlank()) {
            return normalizedFilename;
        }
        String extension = resolveMediaExtension(normalizedFilename, mediaUrl);
        if (extension.isBlank() && temporaryFile != null) {
            extension = extractExtension(temporaryFile.getFileName().toString());
        }
        return extension.isBlank() ? normalizedFilename : normalizedFilename + "." + extension;
    }

    /**
     * 清理单次转写产生的临时文件
     *
     * @param temporaryFile 临时文件路径
     */
    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException e) {
            log.warn("清理视频转写临时文件失败：{}", temporaryFile, e);
        }
    }

    /**
     * 基于本地临时文件的 MultipartFile 适配器
     */
    private static class PathMultipartFile implements MultipartFile {
        private final Path path;
        private final String originalFilename;

        /**
         * 创建临时文件适配器
         *
         * @param path 临时文件路径
         * @param originalFilename 原始文件名
         */
        private PathMultipartFile(Path path, String originalFilename) {
            this.path = path;
            this.originalFilename = originalFilename;
        }

        /**
         * 获取表单字段名
         *
         * @return 表单字段名
         */
        @Override
        public String getName() {
            return "mediaFile";
        }

        /**
         * 获取原始文件名
         *
         * @return 原始文件名
         */
        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        /**
         * 获取媒体类型
         *
         * @return 媒体类型
         */
        @Override
        public String getContentType() {
            try {
                return Files.probeContentType(path);
            } catch (IOException e) {
                return null;
            }
        }

        /**
         * 判断文件是否为空
         *
         * @return 是否为空
         */
        @Override
        public boolean isEmpty() {
            return getSize() == 0L;
        }

        /**
         * 获取文件大小
         *
         * @return 文件字节数
         */
        @Override
        public long getSize() {
            return path.toFile().length();
        }

        /**
         * 读取全部文件字节
         *
         * @return 文件字节
         * @throws IOException 读取失败时抛出
         */
        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        /**
         * 打开文件输入流
         *
         * @return 文件输入流
         * @throws IOException 打开失败时抛出
         */
        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        /**
         * 将文件复制到指定位置
         *
         * @param dest 目标文件
         * @throws IOException 复制失败时抛出
         */
        @Override
        public void transferTo(File dest) throws IOException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
