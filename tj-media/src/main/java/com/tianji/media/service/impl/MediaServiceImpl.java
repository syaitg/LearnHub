package com.tianji.media.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.api.dto.course.MediaQuoteDTO;
import com.tianji.api.dto.course.SectionInfoDTO;
import com.tianji.api.dto.media.MediaAiInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.*;
import com.tianji.media.constants.FileErrorInfo;
import com.tianji.media.domain.dto.MediaDTO;
import com.tianji.media.domain.dto.MediaUploadResultDTO;
import com.tianji.media.domain.po.Media;
import com.tianji.media.domain.query.MediaQuery;
import com.tianji.media.domain.vo.MediaVO;
import com.tianji.media.domain.vo.VideoPlayVO;
import com.tianji.media.enums.FileStatus;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.mapper.MediaMapper;
import com.tianji.media.service.IMediaService;
import com.tianji.media.storage.IMediaStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.media.constants.FileErrorInfo.MEDIA_NOT_EXISTS;

/**
 * <p>
 * 媒资表，主要是视频文件相关服务实现类
 * </p>
 *
 * @author Sy
 * @since 2026-06-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements IMediaService {

    private final IMediaStorage mediaStorage;

    private final CourseClient courseClient;

    private final LearningClient learningClient;

    private final UserClient userClient;

    @Override
    public String getUploadSignature() {
        return mediaStorage.getUploadSignature();
    }

    @Override
    public VideoPlayVO getPlaySignatureBySectionId(Long sectionId) {
        SectionInfoDTO sectionInfo = courseClient.sectionInfo(sectionId);
        AssertUtils.isNotNull(sectionInfo, FileErrorInfo.SECTION_NOT_EXISTS);
        AssertUtils.isNotNull(sectionInfo.getMediaId(), MEDIA_NOT_EXISTS);

        Long lessonId = learningClient.isLessonValid(sectionInfo.getCourseId());
        Integer freeExpire = null;
        if (lessonId == null) {
            if (BooleanUtils.isFalse(sectionInfo.getTrailer())) {
                throw new ForbiddenException(FileErrorInfo.MEDIA_NOT_FREE);
            }
            freeExpire = sectionInfo.getFreeDuration();
        }

        Media media = getById(sectionInfo.getMediaId());
        AssertUtils.isNotNull(media, MEDIA_NOT_EXISTS);
        MediaSource source = resolveSource(media);
        AssertUtils.isTrue(StringUtils.isNotBlank(media.getFileId()), MEDIA_NOT_EXISTS);

        if (source == MediaSource.OWN_TENCENT) {
            ensureMediaUrl(media, source, "sectionId=" + sectionId);
        }

        VideoPlayVO vo = new VideoPlayVO();
        vo.setFileId(media.getFileId());
        vo.setAppId(mediaStorage.getVodAppId(source));
        if (source == MediaSource.OWN_TENCENT) {
            vo.setPlayUrl(mediaStorage.getPlayUrl(media.getMediaUrl(), freeExpire, source));
        } else {
            vo.setSignature(mediaStorage.getPlaySignature(
                    media.getFileId(), UserContext.getUser(), freeExpire, source));
        }
        return vo;
    }

    @Override
    public VideoPlayVO getPlaySignatureByMediaId(Long mediaId) {
        Media media = getById(mediaId);
        AssertUtils.isNotNull(media, MEDIA_NOT_EXISTS);
        MediaSource source = resolveSource(media);
        AssertUtils.isTrue(StringUtils.isNotBlank(media.getFileId()), MEDIA_NOT_EXISTS);

        VideoPlayVO vo = new VideoPlayVO();
        vo.setFileId(media.getFileId());
        vo.setAppId(mediaStorage.getVodAppId(source));
        if (source == MediaSource.OWN_TENCENT) {
            ensureMediaUrl(media, source, "mediaId=" + mediaId);
            vo.setPlayUrl(mediaStorage.getPlayUrl(media.getMediaUrl(), null, source));
        } else {
            vo.setSignature(mediaStorage.getPlaySignature(
                    media.getFileId(), UserContext.getUser(), null, source));
        }
        return vo;
    }

    @Override
    public PageDTO<MediaVO> queryMediaPage(MediaQuery query) {
        Page<Media> mediaPage = new Page<>(query.getPageNo(), query.getPageSize());
        if (StringUtils.isNotBlank(query.getSortBy())) {
            mediaPage.addOrder(new OrderItem().setColumn(query.getSortBy()).setAsc(query.getIsAsc()));
        }
        lambdaQuery()
                .like(StringUtils.isNotBlank(query.getName()), Media::getFilename, query.getName())
                .page(mediaPage);
        List<Media> records = mediaPage.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(mediaPage);
        }
        List<Long> ids = new ArrayList<>(records.size());
        Set<Long> createIds = new HashSet<>();
        for (Media m : records) {
            ids.add(m.getId());
            if (m.getCreater() != null) {
                createIds.add(m.getCreater());
            }
        }
        createIds.remove(0L);

        List<MediaQuoteDTO> quoteList = courseClient.mediaUserInfo(ids);
        Map<Long, Integer> quoteMap = Optional.ofNullable(quoteList)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull)
                .filter(q -> q.getMediaId() != null)
                .collect(Collectors.toMap(MediaQuoteDTO::getMediaId,
                        q -> q.getQuoteNum() == null ? 0 : q.getQuoteNum(), Math::max));

        Map<Long, String> userMap = new HashMap<>();
        if (CollUtils.isNotEmpty(createIds)) {
            List<UserDTO> users = userClient.queryUserByIds(createIds);
            if (CollUtils.isNotEmpty(users)) {
                userMap = users.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(UserDTO::getId, UserDTO::getName, (a, b) -> a));
            }
        }
        List<MediaVO> list = new ArrayList<>(records.size());
        for (Media m : records) {
            MediaVO v = BeanUtils.toBean(m, MediaVO.class);
            v.setUseTimes(quoteMap.getOrDefault(m.getId(), 0));
            if (m.getCreater() != null) {
                v.setCreater(userMap.get(m.getCreater()));
            }
            list.add(v);
        }
        return new PageDTO<>(mediaPage.getTotal(), mediaPage.getPages(), list);
    }

    @Override
    public MediaDTO save(MediaUploadResultDTO result) {
        AssertUtils.isNotNull(result, MEDIA_NOT_EXISTS);
        AssertUtils.isTrue(StringUtils.isNotBlank(result.getFileId()), MEDIA_NOT_EXISTS);
        MediaSource requestedSource = result.getSource() == null ? MediaSource.OWN_TENCENT : result.getSource();

        // 不盲目信任浏览器提交的 VOD 来源，避免历史数据或旧页面把文件归到错误账号。
        MediaSource source = requestedSource;
        MediaSource urlSource = inferSourceFromUrl(result.getMediaUrl());
        if (urlSource != null && urlSource != requestedSource) {
            source = urlSource;
        }
        Media media = queryCloudMediaForSave(source, result.getFileId());
        if (media == null) {
            MediaSource alternativeSource = source == MediaSource.OWN_TENCENT
                    ? MediaSource.OFFICIAL_TENCENT : MediaSource.OWN_TENCENT;
            Media alternativeMedia = queryCloudMediaForSave(alternativeSource, result.getFileId());
            if (alternativeMedia != null) {
                source = alternativeSource;
                media = alternativeMedia;
                log.warn("根据云端归属修正 VOD 来源，fileId={}, requestedSource={}, actualSource={}",
                        result.getFileId(), requestedSource, source);
            }
        }
        if (media == null) {
            media = new Media();
        }
        media.setFileId(result.getFileId());
        media.setSource(source);
        if (StringUtils.isBlank(media.getFilename())) {
            media.setFilename(result.getFilename());
        }
        if (StringUtils.isBlank(media.getCoverUrl())) {
            media.setCoverUrl(result.getCoverUrl());
        }
        if (StringUtils.isBlank(media.getMediaUrl())) {
            media.setMediaUrl(result.getMediaUrl());
        }
        if (media.getSize() == null) {
            media.setSize(result.getSize());
        }
        if (media.getDuration() == null) {
            media.setDuration(result.getDuration());
        }
        media.setMediaUrl(rawMediaUrl(media.getMediaUrl()));
        // 只保存稳定的原始地址，不能把临时签名参数写入数据库。
        FileStatus discoveredStatus = StringUtils.isBlank(media.getMediaUrl())
                ? FileStatus.UPLOADING : FileStatus.UPLOADED;
        if (discoveredStatus == FileStatus.UPLOADING) {
            media.setStatus(FileStatus.UPLOADING);
        } else if (media.getStatus() == null
                || discoveredStatus.getValue() > media.getStatus().getValue()) {
            media.setStatus(discoveredStatus);
        }

        Media old = findExistingMedia(result.getFileId(), source);
        if (old != null) {
            mergeMedia(old, media);
            old.setSource(source);
            boolean updated = updateById(old);
            AssertUtils.isTrue(updated, FileErrorInfo.MEDIA_SAVE_FAILED);
            return BeanUtils.toBean(old, MediaDTO.class);
        }
        boolean saved = save(media);
        AssertUtils.isTrue(saved && media.getId() != null, FileErrorInfo.MEDIA_SAVE_FAILED);
        return BeanUtils.toBean(media, MediaDTO.class);
    }

    /**
     * 查询指定 VOD 账号的媒资；云端暂时不可用时保留本地保存流程，调用方随后尝试另一个账号。
     */
    private Media queryCloudMediaForSave(MediaSource source, String fileId) {
        try {
            return findCloudMedia(mediaStorage.queryMediaInfos(source, fileId), fileId);
        } catch (RuntimeException e) {
            log.warn("查询 VOD 媒资信息失败，source={}, fileId={}", source, fileId, e);
            return null;
        }
    }

    private String rawMediaUrl(String mediaUrl) {
        if (StringUtils.isBlank(mediaUrl)) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(mediaUrl.trim());
            if ((!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                    || StringUtils.isBlank(uri.getHost())
                    || StringUtils.isBlank(uri.getRawPath())
                    || "/".equals(uri.getRawPath())) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 确保媒资具备稳定的原始播放地址；缺失或非法时按 fileId + source 查询对应 VOD 账号。
     */
    private void ensureMediaUrl(Media media, MediaSource source, String scene) {
        MediaSource originalSource = media.getSource();
        media.setSource(source);
        if (StringUtils.isNotBlank(media.getMediaUrl())) {
            String rawUrl = rawMediaUrl(media.getMediaUrl());
            if (StringUtils.isNotBlank(rawUrl)) {
                boolean sourceChanged = originalSource != source;
                boolean urlChanged = !rawUrl.equals(media.getMediaUrl());
                if (sourceChanged || urlChanged) {
                    media.setMediaUrl(rawUrl);
                    boolean updated = updateById(media);
                    AssertUtils.isTrue(updated, FileErrorInfo.MEDIA_NOT_READY);
                }
                return;
            }
            media.setMediaUrl(null);
        }
        try {
            List<Media> refreshed = mediaStorage.queryMediaInfos(source, media.getFileId());
            if (CollUtils.isNotEmpty(refreshed)) {
                Media refreshedMedia = findCloudMedia(refreshed, media.getFileId());
                if (refreshedMedia == null) {
                    throw new CommonException(FileErrorInfo.MEDIA_NOT_READY);
                }
                mergeMedia(media, refreshedMedia);
                media.setSource(source);
                String rawUrl = rawMediaUrl(media.getMediaUrl());
                media.setMediaUrl(rawUrl);
                if (StringUtils.isNotBlank(rawUrl)) {
                    boolean updated = updateById(media);
                    AssertUtils.isTrue(updated, FileErrorInfo.MEDIA_NOT_READY);
                }
            }
        } catch (RuntimeException e) {
            log.warn("按来源查询腾讯云 VOD 媒资信息失败，scene={}, mediaId={}, fileId={}, source={}",
                    scene, media.getId(), media.getFileId(), source, e);
        }
        if (StringUtils.isBlank(media.getMediaUrl())) {
            throw new CommonException(FileErrorInfo.MEDIA_NOT_READY);
        }
    }

    /**
     * 按文件 ID 和 VOD 来源查找已有媒资记录。
     * 历史官方媒资的 source 可能为空，需要兼容按官方账号匹配。
     */
    private Media findExistingMedia(String fileId, MediaSource source) {
        Media media = lambdaQuery()
                .eq(Media::getFileId, fileId)
                .eq(Media::getSource, source)
                .one();
        if (media == null && source == MediaSource.OFFICIAL_TENCENT) {
            media = lambdaQuery()
                    .eq(Media::getFileId, fileId)
                    .isNull(Media::getSource)
                    .one();
        }
        return media;
    }

    private void mergeMedia(Media target, Media latest) {
        if (StringUtils.isNotBlank(latest.getFilename())) {
            target.setFilename(latest.getFilename());
        }
        String latestRawUrl = rawMediaUrl(latest.getMediaUrl());
        if (StringUtils.isNotBlank(latestRawUrl)) {
            target.setMediaUrl(latestRawUrl);
        }
        if (StringUtils.isNotBlank(latest.getCoverUrl())) {
            target.setCoverUrl(latest.getCoverUrl());
        }
        if (latest.getSize() != null) {
            target.setSize(latest.getSize());
        }
        if (latest.getDuration() != null) {
            target.setDuration(latest.getDuration());
        }
        if (latest.getStatus() != null
                && (target.getStatus() == null
                || latest.getStatus().getValue() > target.getStatus().getValue())) {
            target.setStatus(latest.getStatus());
        }
    }

    @Override
    public void updateMediaProcedureResult(Media media) {
        AssertUtils.isNotNull(media, MEDIA_NOT_EXISTS);
        AssertUtils.isTrue(StringUtils.isNotBlank(media.getFileId()), MEDIA_NOT_EXISTS);
        MediaSource source = normalizeSource(media.getSource());
        media.setSource(source);
        media.setMediaUrl(rawMediaUrl(media.getMediaUrl()));
        if (StringUtils.isBlank(media.getMediaUrl())) {
            media.setStatus(FileStatus.UPLOADING);
        } else if (media.getStatus() == null) {
            media.setStatus(FileStatus.UPLOADED);
        }
        Media old = findExistingMedia(media.getFileId(), source);
        if (old == null) {
            // 云端回调完成后保存新的媒资记录
            boolean saved = save(media);
            AssertUtils.isTrue(saved && media.getId() != null, FileErrorInfo.MEDIA_NOT_READY);
        } else {
            // 媒资记录已存在时更新已有记录
            boolean statusShouldAdvance = media.getStatus() != null
                    && (old.getStatus() == null
                    || media.getStatus().getValue() > old.getStatus().getValue());
            var updateChain = lambdaUpdate()
                    .set(Media::getSource, source)
                    .set(StringUtils.isNotBlank(media.getFilename()), Media::getFilename, media.getFilename())
                    .set(StringUtils.isNotBlank(media.getMediaUrl()), Media::getMediaUrl, media.getMediaUrl())
                    .set(StringUtils.isNotBlank(media.getCoverUrl()), Media::getCoverUrl, media.getCoverUrl())
                    .set(media.getDuration() != null, Media::getDuration, media.getDuration())
                    .set(media.getSize() != null, Media::getSize, media.getSize());
            if (statusShouldAdvance) {
                updateChain.set(Media::getStatus, media.getStatus());
            }
            boolean updated = updateChain.eq(Media::getId, old.getId()).update();
            AssertUtils.isTrue(updated, FileErrorInfo.MEDIA_NOT_READY);
        }
    }

    /**
     * 查询视频 AI 处理所需的媒资信息
     *
     * @param mediaId 媒资 ID
     * @param courseId 课程 ID
     * @param sectionId 小节 ID
     * @return 媒资 AI 信息
     */
    @Override
    public MediaAiInfoDTO queryAiInfo(Long mediaId, Long courseId, Long sectionId) {
        if (!WebUtils.isFeignRequest()) {
            throw new ForbiddenException("媒资 AI 信息仅允许内部服务调用");
        }
        Media media = getById(mediaId);
        AssertUtils.isNotNull(media, MEDIA_NOT_EXISTS);

        SectionInfoDTO section = courseClient.sectionInfo(sectionId);
        if (section == null
                || !Objects.equals(section.getCourseId(), courseId)
                || !Objects.equals(section.getMediaId(), mediaId)) {
            throw new ForbiddenException("媒资未关联指定课程小节，无法进行 AI 处理");
        }

        CourseBaseInfoDTO course = courseClient.baseInfo(courseId, true);
        Long userId = UserContext.getUser();
        if (userId == null
                || course == null
                || !userId.equals(course.getCreater())) {
            throw new ForbiddenException("只有课程创建者可以处理该课程的视频媒资");
        }

        MediaSource source = resolveSource(media);
        media.setSource(source);
        AssertUtils.isTrue(StringUtils.isNotBlank(media.getFileId()), MEDIA_NOT_EXISTS);
        ensureMediaUrl(media, source, "aiInfo mediaId=" + mediaId);

        // AI 服务需要直接下载媒资。数据库中保存的是不带临时鉴权参数的原始地址，
        // 直接返回该地址会被腾讯云防盗链校验拒绝（通常返回 403）。
        // 复用播放地址生成逻辑，为内部转写请求生成当前来源对应的临时签名地址。
        String aiMediaUrl = mediaStorage.getPlayUrl(media.getMediaUrl(), null, source);
        if (StringUtils.isBlank(aiMediaUrl)) {
            throw new CommonException("无法生成视频 AI 转写所需的媒资访问地址");
        }

        MediaAiInfoDTO result = new MediaAiInfoDTO();
        result.setMediaId(media.getId());
        result.setFileId(media.getFileId());
        result.setFilename(media.getFilename());
        result.setMediaUrl(aiMediaUrl);
        result.setDuration(media.getDuration());
        result.setSize(media.getSize());
        result.setStatus(media.getStatus() == null ? null : media.getStatus().getValue());
        result.setCreater(media.getCreater());
        return result;
    }

    /**
     * 删除单个媒资。先校验课程引用关系，再删除数据库记录和云端文件。
     *
     * @param mediaId 媒资 ID
     */
    @Override
    public void deleteMedia(Long mediaId) {
        List<Media> medias = validateDeletableMedias(CollUtils.singletonList(mediaId));
        Media media = medias.get(0);
        deleteCloudMedia(media);
        boolean removed;
        try {
            removed = removeById(mediaId);
        } catch (RuntimeException e) {
            log.error("删除媒资数据库记录失败，mediaId={}, fileId={}, source={}",
                    media.getId(), media.getFileId(), normalizeSource(media.getSource()), e);
            throw e;
        }
        if (!removed) {
            log.error("删除媒资数据库记录未生效，云端文件可能已删除，mediaId={}, fileId={}, source={}",
                    media.getId(), media.getFileId(), normalizeSource(media.getSource()));
        }
        AssertUtils.isTrue(removed, FileErrorInfo.MEDIA_DELETE_FAILED);
    }

    /**
     * 批量删除媒资。输入 ID 会先去重，并且仅在全部媒资存在且均未被课程引用时执行删除。
     *
     * @param mediaIds 媒资 ID 集合
     */
    @Override
    public void deleteMedias(List<Long> mediaIds) {
        List<Media> medias = validateDeletableMedias(mediaIds);
        // 先删除云端文件，再删除数据库记录；云端 API 不受数据库事务回滚控制。
        for (Media media : medias) {
            deleteCloudMedia(media);
        }
        List<Long> ids = medias.stream().map(Media::getId).collect(Collectors.toList());
        boolean removed;
        try {
            removed = removeByIds(ids);
        } catch (RuntimeException e) {
            log.error("批量删除媒资数据库记录失败，mediaIds={}, fileIds={}, sources={}",
                    ids,
                    medias.stream().map(Media::getFileId).collect(Collectors.toList()),
                    medias.stream().map(media -> normalizeSource(media.getSource())).collect(Collectors.toList()), e);
            throw e;
        }
        if (!removed) {
            log.error("批量删除媒资数据库记录未生效，云端文件可能已删除，mediaIds={}, fileIds={}, sources={}",
                    ids,
                    medias.stream().map(Media::getFileId).collect(Collectors.toList()),
                    medias.stream().map(media -> normalizeSource(media.getSource())).collect(Collectors.toList()));
        }
        AssertUtils.isTrue(removed, FileErrorInfo.MEDIA_DELETE_FAILED);
    }

    /**
        // 课程服务必须返回每个媒资的引用统计；缺少结果时拒绝删除，不能默认为 0。
     */
    private List<Media> validateDeletableMedias(List<Long> mediaIds) {
        AssertUtils.isNotEmpty(mediaIds, FileErrorInfo.MEDIA_ID_REQUIRED);
        AssertUtils.isFalse(mediaIds.stream().anyMatch(Objects::isNull), FileErrorInfo.MEDIA_ID_REQUIRED);

        List<Long> distinctIds = mediaIds.stream().distinct().collect(Collectors.toList());
        List<Media> medias = listByIds(distinctIds);
        AssertUtils.isNotEmpty(medias, MEDIA_NOT_EXISTS);
        Map<Long, Media> mediaMap = medias.stream()
                .filter(Objects::nonNull)
                .filter(media -> media.getId() != null)
                .collect(Collectors.toMap(Media::getId, media -> media, (left, right) -> left));
        AssertUtils.isTrue(mediaMap.keySet().containsAll(distinctIds), MEDIA_NOT_EXISTS);

        // 课程服务必须返回每个媒资的引用统计；缺少结果时拒绝删除，不能默认为0。
        List<MediaQuoteDTO> quoteList = courseClient.mediaUserInfo(distinctIds);
        AssertUtils.isNotEmpty(quoteList, FileErrorInfo.MEDIA_QUOTE_NOT_EXISTS);
        Map<Long, Integer> quoteMap = Optional.ofNullable(quoteList)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull)
                .filter(quote -> quote.getMediaId() != null)
                .collect(Collectors.toMap(
                        MediaQuoteDTO::getMediaId,
                        quote -> quote.getQuoteNum() == null ? 0 : quote.getQuoteNum(),
                        Math::max));
        AssertUtils.isTrue(quoteMap.keySet().containsAll(distinctIds),
                FileErrorInfo.MEDIA_QUOTE_NOT_EXISTS);
        boolean inUse = distinctIds.stream().anyMatch(id -> quoteMap.getOrDefault(id, 0) > 0);
        AssertUtils.isFalse(inUse, FileErrorInfo.MEDIA_IN_USE);

        List<Media> orderedMedias = distinctIds.stream().map(mediaMap::get).collect(Collectors.toList());
        AssertUtils.isFalse(
                orderedMedias.stream().anyMatch(media -> StringUtils.isBlank(media.getFileId())),
                MEDIA_NOT_EXISTS);
        return orderedMedias;
    }

    /** 历史媒资没有 source 字段时，统一按官方账号处理。 */
    private void deleteCloudMedia(Media media) {
        MediaSource source;
        try {
            source = resolveSourceForDelete(media);
        } catch (RuntimeException e) {
            log.error("无法确认媒资删除所使用的 VOD 账号，拒绝删除，mediaId={}, fileId={}, storedSource={}",
                    media.getId(), media.getFileId(), media.getSource(), e);
            throw e;
        }
        try {
            mediaStorage.deleteFile(media.getFileId(), source);
        } catch (RuntimeException e) {
            log.error("删除云端媒资失败，mediaId={}, fileId={}, source={}",
                    media.getId(), media.getFileId(), source, e);
            throw e;
        }
    }

    /**
     * 解析删除操作使用的 VOD 来源，避免仅凭历史 source 或默认值删除错误账号中的文件。
     * 当来源无法唯一确认时必须拒绝删除，不能把云端 API 异常当成文件不存在。
     */
    private MediaSource resolveSourceForDelete(Media media) {
        AssertUtils.isTrue(StringUtils.isNotBlank(media.getFileId()), MEDIA_NOT_EXISTS);
        MediaSource stored = media.getSource();
        MediaSource inferred = inferSourceFromUrl(media.getMediaUrl());

        // 当前记录如果明确指定来源，且地址没有明确指向另一个账号，则优先使用已保存来源。
        // 删除前保留来源判断结果，避免误删另一账号中的同名媒资。
        if (stored != null && (inferred == null || inferred == stored)) {
            return stored;
        }

        List<MediaSource> candidates = new ArrayList<>(2);
        if (inferred != null) {
            candidates.add(inferred);
        }
        if (stored != null && !candidates.contains(stored)) {
            candidates.add(stored);
        }
        if (stored == null) {
            candidates.clear();
            candidates.add(MediaSource.OFFICIAL_TENCENT);
            candidates.add(MediaSource.OWN_TENCENT);
        }

        List<MediaSource> confirmed = new ArrayList<>(2);
        for (MediaSource candidate : candidates) {
            if (findCloudMediaForDelete(candidate, media.getFileId()) != null) {
                confirmed.add(candidate);
            }
        }
        if (confirmed.size() == 1) {
            return confirmed.get(0);
        }
        throw new CommonException(FileErrorInfo.MEDIA_SOURCE_NOT_CONFIRMED);
    }

    private Media findCloudMediaForDelete(MediaSource source, String fileId) {
        // 删除操作不能吞掉云端 API 异常，否则可能把“无法确认”误判为“文件不存在”。
        // 此处不捕获云端异常，无法确认文件归属时必须终止删除。
        return findCloudMedia(mediaStorage.queryMediaInfos(source, fileId), fileId);
    }

    private Media findCloudMedia(List<Media> medias, String fileId) {
        if (CollUtils.isEmpty(medias) || StringUtils.isBlank(fileId)) {
            return null;
        }
        return medias.stream()
                .filter(Objects::nonNull)
                .filter(media -> fileId.equals(media.getFileId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析历史媒资播放所使用的 VOD 来源。
     *
     * <p>旧版本没有保存 {@code source}，或迁移时错误地标记为官方账号。自有 VOD 地址包含账号 APPID，
     * 因此可以优先根据地址纠正来源；地址不完整时再按 fileId 查询两个已配置账号，避免生成错误的官方 psign。</p>
     *
     */
    private MediaSource resolveSource(Media media) {
        MediaSource stored = normalizeSource(media.getSource());
        MediaSource inferred = inferSourceFromUrl(media.getMediaUrl());
        if (inferred != null) {
            // 在纠正来源的同时清理地址中的临时 t、exper、sign、psign 参数。
            String originalUrl = media.getMediaUrl();
            String rawUrl = rawMediaUrl(originalUrl);
            boolean urlChanged = StringUtils.isNotBlank(rawUrl) && !rawUrl.equals(originalUrl);
            if (StringUtils.isNotBlank(rawUrl)) {
                media.setMediaUrl(rawUrl);
            }
            if (inferred != stored || urlChanged) {
                repairSource(media, inferred);
            }
            return inferred;
        }

        // 自定义域名无法直接判断账号时，仅对需要恢复的历史记录查询两个 VOD 账号。
        if (StringUtils.isNotBlank(media.getFileId())
                && (media.getSource() == null || stored == MediaSource.OFFICIAL_TENCENT)) {
            MediaSource other = stored == MediaSource.OWN_TENCENT
                    ? MediaSource.OFFICIAL_TENCENT : MediaSource.OWN_TENCENT;
            Media currentMedia = queryCloudMedia(stored, media.getFileId(), media);
            if (currentMedia != null) {
                mergeMedia(media, currentMedia);
                media.setSource(stored);
                persistResolvedMedia(media);
                return stored;
            }
            Media alternativeMedia = queryCloudMedia(other, media.getFileId(), media);
            if (alternativeMedia != null) {
                mergeMedia(media, alternativeMedia);
                repairSource(media, other);
                return other;
            }
        }
        return stored;
    }

    private Media findCloudMediaBySource(MediaSource source, String fileId) {
        try {
            return findCloudMedia(mediaStorage.queryMediaInfos(source, fileId), fileId);
        } catch (RuntimeException e) {
            log.warn("按来源查询 VOD 媒资失败，source={}, fileId={}", source, fileId, e);
            return null;
        }
    }

    private Media queryCloudMedia(MediaSource source, String fileId, Media localMedia) {
        Media cloudMedia = findCloudMediaBySource(source, fileId);
        if (cloudMedia == null && localMedia != null) {
            log.debug("按来源查询 VOD 媒资未找到记录，mediaId={}, fileId={}, source={}",
                    localMedia.getId(), fileId, source);
        }
        return cloudMedia;
    }

    private void persistResolvedMedia(Media media) {
        if (media.getId() == null) {
            return;
        }
        boolean updated = updateById(media);
        AssertUtils.isTrue(updated, FileErrorInfo.MEDIA_NOT_READY);
    }

    private MediaSource inferSourceFromUrl(String mediaUrl) {
        if (StringUtils.isBlank(mediaUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(mediaUrl.trim());
            String host = uri.getHost();
            if (StringUtils.isBlank(host)) {
                return null;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            Long ownAppId = configuredVodAppId(MediaSource.OWN_TENCENT);
            if (matchesVodAppId(normalizedHost, ownAppId)) {
                return MediaSource.OWN_TENCENT;
            }
            Long officialAppId = configuredVodAppId(MediaSource.OFFICIAL_TENCENT);
            if (matchesVodAppId(normalizedHost, officialAppId)) {
                return MediaSource.OFFICIAL_TENCENT;
            }
        } catch (RuntimeException e) {
            // 地址无效或无法识别时，交给下面的云端查询逻辑兜底。
        }
        return null;
    }

    private Long configuredVodAppId(MediaSource source) {
        try {
            return mediaStorage.getVodAppId(source);
        } catch (RuntimeException e) {
            log.debug("无法获取 {} VOD APPID，跳过地址来源判断", source, e);
            return null;
        }
    }

    private boolean matchesVodAppId(String host, Long appId) {
        if (appId == null || appId <= 0) {
            return false;
        }
        String prefix = String.valueOf(appId).toLowerCase(Locale.ROOT) + ".";
        return host.startsWith(prefix);
    }

    private void repairSource(Media media, MediaSource source) {
        media.setSource(source);
        persistResolvedMedia(media);
    }

    private MediaSource normalizeSource(MediaSource source) {
        return source == null ? MediaSource.OFFICIAL_TENCENT : source;
    }

}
