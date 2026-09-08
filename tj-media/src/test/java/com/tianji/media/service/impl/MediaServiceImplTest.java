package com.tianji.media.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.MediaQuoteDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.CommonException;
import com.tianji.media.constants.FileErrorInfo;
import com.tianji.media.domain.dto.MediaUploadResultDTO;
import com.tianji.media.domain.po.Media;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.mapper.MediaMapper;
import com.tianji.media.storage.IMediaStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Media deletion and playback authorization tests.
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock
    private IMediaStorage mediaStorage;
    @Mock
    private CourseClient courseClient;
    @Mock
    private LearningClient learningClient;
    @Mock
    private UserClient userClient;
    @Mock
    private MediaMapper mediaMapper;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(mediaStorage, courseClient, learningClient, userClient);
        ReflectionTestUtils.setField(mediaService, "baseMapper", mediaMapper);
        ReflectionTestUtils.setField(mediaService, "entityClass", Media.class);
    }

    @AfterEach
    void tearDown() {
        com.tianji.common.utils.UserContext.removeUser();
    }

    @Test
    @DisplayName("Reject deleting media that is referenced by a course")
    void shouldRejectDeletingMediaInUse() {
        Media media = media(1L, "file-1");
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(media));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(new MediaQuoteDTO(1L, 1)));

        assertThatThrownBy(() -> mediaService.deleteMedia(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(FileErrorInfo.MEDIA_IN_USE);

        verify(mediaMapper, never()).deleteById(1L);
        verify(mediaStorage, never()).deleteFile("file-1", MediaSource.OFFICIAL_TENCENT);
    }

    @Test
    @DisplayName("Delete one unused media record and its cloud file")
    void shouldDeleteSingleMediaAndCloudFile() {
        Media media = media(1L, "file-1");
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(media));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(new MediaQuoteDTO(1L, 0)));
        when(mediaMapper.deleteById(1L)).thenReturn(1);

        mediaService.deleteMedia(1L);

        verify(mediaMapper).deleteById(1L);
        verify(mediaStorage).deleteFile("file-1", MediaSource.OFFICIAL_TENCENT);
    }

    @Test
    @DisplayName("Deduplicate IDs during batch deletion and delete every cloud file")
    void shouldDeleteDistinctMediaInBatch() {
        Media first = media(1L, "file-1");
        Media second = media(2L, "file-2");
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(first, second));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(
                new MediaQuoteDTO(1L, 0), new MediaQuoteDTO(2L, 0)));
        when(mediaMapper.deleteByIds(anyCollection())).thenReturn(2);

        mediaService.deleteMedias(List.of(1L, 2L, 1L));

        verify(mediaMapper).deleteByIds(List.of(1L, 2L));
        verify(mediaStorage).deleteFile("file-1", MediaSource.OFFICIAL_TENCENT);
        verify(mediaStorage).deleteFile("file-2", MediaSource.OFFICIAL_TENCENT);
    }

    @Test
    @DisplayName("Reject missing media without querying course references")
    void shouldRejectMissingMedia() {
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> mediaService.deleteMedia(99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(FileErrorInfo.MEDIA_NOT_EXISTS);

        verify(courseClient, never()).mediaUserInfo(anyCollection());
        verify(mediaStorage, never()).deleteFile("file-99");
    }

    @Test
    @DisplayName("Reject preview for missing media instead of throwing NPE")
    void shouldRejectPreviewForMissingMedia() {
        when(mediaMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> mediaService.getPlaySignatureByMediaId(99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(FileErrorInfo.MEDIA_NOT_EXISTS);

        verify(mediaStorage, never()).getPlaySignature(eq("file-99"), any(), eq(null));
    }

    @Test
    @DisplayName("Reject playback for missing section instead of throwing NPE")
    void shouldRejectPlayForMissingSection() {
        when(courseClient.sectionInfo(99L)).thenReturn(null);

        assertThatThrownBy(() -> mediaService.getPlaySignatureBySectionId(99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(FileErrorInfo.SECTION_NOT_EXISTS);

        verify(learningClient, never()).isLessonValid(any());
    }

    @Test
    @DisplayName("Return anti-leech URL for own media without generating psign")
    void shouldReturnPlayUrlForOwnMedia() {
        Media media = media(10L, "own-file-id")
                .setSource(MediaSource.OWN_TENCENT)
                .setMediaUrl("https://example.vod-qcloud.com/path/video.mp4");
        when(mediaMapper.selectById(10L)).thenReturn(media);
        when(mediaStorage.getVodAppId(MediaSource.OWN_TENCENT)).thenReturn(1479043174L);
        when(mediaStorage.getPlayUrl(media.getMediaUrl(), null, MediaSource.OWN_TENCENT))
                .thenReturn(media.getMediaUrl() + "?t=1&sign=test");

        var result = mediaService.getPlaySignatureByMediaId(10L);

        assertThat(result.getPlayUrl()).isEqualTo(media.getMediaUrl() + "?t=1&sign=test");
        assertThat(result.getSignature()).isNull();
        assertThat(result.getAppId()).isEqualTo(1479043174L);
        verify(mediaStorage).getPlayUrl(media.getMediaUrl(), null, MediaSource.OWN_TENCENT);
        verify(mediaStorage, never()).getPlaySignature(any(), any(), any(), eq(MediaSource.OWN_TENCENT));
    }

    @Test
    @DisplayName("Repair a legacy own-VOD record that was migrated as official")
    void shouldRepairLegacyOwnSourceFromVodUrl() {
        Media media = media(12L, "own-file-12")
                .setSource(MediaSource.OFFICIAL_TENCENT)
                .setMediaUrl("https://1479043174.vod-qcloud.com/path/video.mp4");
        when(mediaMapper.selectById(12L)).thenReturn(media);
        when(mediaStorage.getVodAppId(MediaSource.OWN_TENCENT)).thenReturn(1479043174L);
        when(mediaStorage.getPlayUrl(media.getMediaUrl(), null, MediaSource.OWN_TENCENT))
                .thenReturn(media.getMediaUrl() + "?t=1&sign=test");
        when(mediaMapper.updateById(media)).thenReturn(1);

        var result = mediaService.getPlaySignatureByMediaId(12L);

        assertThat(result.getPlayUrl()).isEqualTo(media.getMediaUrl() + "?t=1&sign=test");
        assertThat(result.getSignature()).isNull();
        assertThat(media.getSource()).isEqualTo(MediaSource.OWN_TENCENT);
        verify(mediaMapper).updateById(media);
        verify(mediaStorage).getPlayUrl(media.getMediaUrl(), null, MediaSource.OWN_TENCENT);
        verify(mediaStorage, never()).getPlaySignature(any(), any(), any(), eq(MediaSource.OFFICIAL_TENCENT));
    }

    @Test
    @DisplayName("Find the correct VOD account for a legacy record without URL")
    void shouldRecoverLegacySourceByCloudLookup() {
        Media media = media(13L, "own-file-13").setSource(MediaSource.OFFICIAL_TENCENT);
        Media cloudMedia = media(null, "own-file-13")
                .setMediaUrl("https://1479043174.vod-qcloud.com/path/video.mp4")
                .setSource(MediaSource.OWN_TENCENT);
        when(mediaMapper.selectById(13L)).thenReturn(media);
        when(mediaStorage.queryMediaInfos(MediaSource.OFFICIAL_TENCENT, "own-file-13"))
                .thenReturn(List.of());
        when(mediaStorage.queryMediaInfos(MediaSource.OWN_TENCENT, "own-file-13"))
                .thenReturn(List.of(cloudMedia));
        when(mediaStorage.getVodAppId(MediaSource.OWN_TENCENT)).thenReturn(1479043174L);
        when(mediaStorage.getPlayUrl(cloudMedia.getMediaUrl(), null, MediaSource.OWN_TENCENT))
                .thenReturn(cloudMedia.getMediaUrl() + "?t=1&sign=test");
        when(mediaMapper.updateById(media)).thenReturn(1);

        var result = mediaService.getPlaySignatureByMediaId(13L);

        assertThat(result.getPlayUrl()).isEqualTo(cloudMedia.getMediaUrl() + "?t=1&sign=test");
        assertThat(media.getSource()).isEqualTo(MediaSource.OWN_TENCENT);
        assertThat(media.getMediaUrl()).isEqualTo(cloudMedia.getMediaUrl());
        verify(mediaMapper).updateById(media);
        verify(mediaStorage).queryMediaInfos(MediaSource.OFFICIAL_TENCENT, "own-file-13");
        verify(mediaStorage).queryMediaInfos(MediaSource.OWN_TENCENT, "own-file-13");
    }

    @Test
    @DisplayName("Return psign for official media without generating anti-leech URL")
    void shouldReturnPsignForOfficialMedia() {
        Media media = media(11L, "official-file-id").setSource(MediaSource.OFFICIAL_TENCENT);
        when(mediaMapper.selectById(11L)).thenReturn(media);
        when(mediaStorage.getVodAppId(MediaSource.OFFICIAL_TENCENT)).thenReturn(1312394356L);
        when(mediaStorage.getPlaySignature("official-file-id", null, null, MediaSource.OFFICIAL_TENCENT))
                .thenReturn("official-psign");

        var result = mediaService.getPlaySignatureByMediaId(11L);

        assertThat(result.getSignature()).isEqualTo("official-psign");
        assertThat(result.getPlayUrl()).isNull();
        assertThat(result.getAppId()).isEqualTo(1312394356L);
        verify(mediaStorage, never()).getPlayUrl(any(), any(), eq(MediaSource.OFFICIAL_TENCENT));
    }

    @Test
    @DisplayName("Default upload source to own VOD and strip temporary URL query")
    void shouldDefaultUploadMediaToOwnVodAndStripQuery() {
        when(mediaMapper.selectOne(any())).thenReturn(null);
        when(mediaMapper.insert(any(Media.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Media.class).setId(20L);
            return 1;
        });

        var result = mediaService.save(MediaUploadResultDTO.builder()
                .fileId("own-file-20")
                .filename("lesson.mp4")
                .mediaUrl("https://example.vod-qcloud.com/path/lesson.mp4?t=abc&sign=def&exper=60")
                .build());

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaMapper).insert(captor.capture());
        Media saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(MediaSource.OWN_TENCENT);
        assertThat(saved.getMediaUrl()).isEqualTo("https://example.vod-qcloud.com/path/lesson.mp4");
        verify(mediaStorage).queryMediaInfos(MediaSource.OWN_TENCENT, "own-file-20");
        assertThat(result.getId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Correct a mismatched requested source using the VOD account that owns the file")
    void shouldCorrectMismatchedUploadSource() {
        Media officialCloudMedia = media(31L, "official-file-31")
                .setSource(MediaSource.OFFICIAL_TENCENT)
                .setFilename("official.mp4")
                .setMediaUrl("https://1312394356.vod-qcloud.com/video/official.mp4");
        when(mediaStorage.queryMediaInfos(MediaSource.OWN_TENCENT, "official-file-31"))
                .thenReturn(List.of());
        when(mediaStorage.queryMediaInfos(MediaSource.OFFICIAL_TENCENT, "official-file-31"))
                .thenReturn(List.of(officialCloudMedia));
        when(mediaMapper.selectOne(any())).thenReturn(null);
        when(mediaMapper.insert(any(Media.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Media.class).setId(31L);
            return 1;
        });

        mediaService.save(MediaUploadResultDTO.builder()
                .fileId("official-file-31")
                .source(MediaSource.OWN_TENCENT)
                .filename("stale-name.mp4")
                .mediaUrl("https://1312394356.vod-qcloud.com/video/official.mp4")
                .build());

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaMapper).insert(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(MediaSource.OFFICIAL_TENCENT);
        assertThat(captor.getValue().getMediaUrl())
                .isEqualTo("https://1312394356.vod-qcloud.com/video/official.mp4");
        verify(mediaStorage).queryMediaInfos(MediaSource.OWN_TENCENT, "official-file-31");
        verify(mediaStorage).queryMediaInfos(MediaSource.OFFICIAL_TENCENT, "official-file-31");
    }

    @Test
    @DisplayName("Reuse legacy official media with null source")
    void shouldReuseLegacyOfficialMediaWithNullSource() {
        Media legacy = media(21L, "official-file-21").setMediaUrl("https://example.com/old.mp4");
        when(mediaMapper.selectOne(any())).thenReturn(null, legacy);
        when(mediaMapper.updateById(any(Media.class))).thenReturn(1);

        var result = mediaService.save(MediaUploadResultDTO.builder()
                .fileId("official-file-21")
                .source(MediaSource.OFFICIAL_TENCENT)
                .filename("lesson.mp4")
                .mediaUrl("https://example.com/lesson.mp4?psign=temporary")
                .build());

        assertThat(result.getId()).isEqualTo(21L);
        verify(mediaMapper).updateById(legacy);
        verify(mediaMapper, never()).insert(any(Media.class));
    }

    @Test
    @DisplayName("Delete own media from own VOD")
    void shouldDeleteOwnMediaFromOwnVod() {
        Media media = media(22L, "own-file-22").setSource(MediaSource.OWN_TENCENT);
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(media));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(new MediaQuoteDTO(22L, 0)));
        when(mediaMapper.deleteById(22L)).thenReturn(1);

        mediaService.deleteMedia(22L);

        verify(mediaStorage).deleteFile("own-file-22", MediaSource.OWN_TENCENT);
        verify(mediaMapper).deleteById(22L);
    }

    @Test
    @DisplayName("Resolve legacy null-source media to own VOD before deleting")
    void shouldDeleteLegacyNullSourceFromOwnVod() {
        Media media = media(25L, "legacy-own-file-25")
                .setSource(null)
                .setMediaUrl("https://1479043174.vod-qcloud.com/video/legacy.mp4");
        Media cloudMedia = media(null, "legacy-own-file-25")
                .setSource(MediaSource.OWN_TENCENT)
                .setMediaUrl(media.getMediaUrl());
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(media));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(new MediaQuoteDTO(25L, 0)));
        when(mediaStorage.getVodAppId(MediaSource.OWN_TENCENT)).thenReturn(1479043174L);
        when(mediaStorage.queryMediaInfos(MediaSource.OWN_TENCENT, "legacy-own-file-25"))
                .thenReturn(List.of(cloudMedia));
        when(mediaStorage.queryMediaInfos(MediaSource.OFFICIAL_TENCENT, "legacy-own-file-25"))
                .thenReturn(List.of());
        when(mediaMapper.deleteById(25L)).thenReturn(1);

        mediaService.deleteMedia(25L);

        verify(mediaStorage).deleteFile("legacy-own-file-25", MediaSource.OWN_TENCENT);
        verify(mediaStorage, never()).deleteFile("legacy-own-file-25", MediaSource.OFFICIAL_TENCENT);
    }

    @Test
    @DisplayName("Reject legacy null-source media when neither VOD account confirms ownership")
    void shouldRejectDeleteWhenLegacySourceCannotBeConfirmed() {
        Media media = media(26L, "legacy-unknown-file-26").setSource(null);
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(media));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(new MediaQuoteDTO(26L, 0)));
        when(mediaStorage.queryMediaInfos(MediaSource.OFFICIAL_TENCENT, "legacy-unknown-file-26"))
                .thenReturn(List.of());
        when(mediaStorage.queryMediaInfos(MediaSource.OWN_TENCENT, "legacy-unknown-file-26"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> mediaService.deleteMedia(26L))
                .isInstanceOf(CommonException.class)
                .hasMessage(FileErrorInfo.MEDIA_SOURCE_NOT_CONFIRMED);

        verify(mediaStorage, never()).deleteFile(any(), any());
        verify(mediaMapper, never()).deleteById(26L);
    }

    @Test
    @DisplayName("Reject legacy null-source media when the same file ID exists in both VOD accounts")
    void shouldRejectDeleteWhenLegacySourceIsAmbiguous() {
        Media media = media(27L, "legacy-duplicate-file-27").setSource(null);
        Media official = media(null, "legacy-duplicate-file-27").setSource(MediaSource.OFFICIAL_TENCENT);
        Media own = media(null, "legacy-duplicate-file-27").setSource(MediaSource.OWN_TENCENT);
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(media));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(new MediaQuoteDTO(27L, 0)));
        when(mediaStorage.queryMediaInfos(MediaSource.OFFICIAL_TENCENT, "legacy-duplicate-file-27"))
                .thenReturn(List.of(official));
        when(mediaStorage.queryMediaInfos(MediaSource.OWN_TENCENT, "legacy-duplicate-file-27"))
                .thenReturn(List.of(own));

        assertThatThrownBy(() -> mediaService.deleteMedia(27L))
                .isInstanceOf(CommonException.class)
                .hasMessage(FileErrorInfo.MEDIA_SOURCE_NOT_CONFIRMED);

        verify(mediaStorage, never()).deleteFile(any(), any());
        verify(mediaMapper, never()).deleteById(27L);
    }

    @Test
    @DisplayName("Reject batch delete when quote data is incomplete")
    void shouldRejectBatchDeleteWhenQuoteMissing() {
        Media first = media(23L, "file-23");
        Media second = media(24L, "file-24");
        when(mediaMapper.selectByIds(anyCollection())).thenReturn(List.of(first, second));
        when(courseClient.mediaUserInfo(anyCollection())).thenReturn(List.of(new MediaQuoteDTO(23L, 0)));

        assertThatThrownBy(() -> mediaService.deleteMedias(List.of(23L, 24L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(FileErrorInfo.MEDIA_QUOTE_NOT_EXISTS);

        verify(mediaStorage, never()).deleteFile(any(), any());
        verify(mediaMapper, never()).deleteByIds(anyCollection());
    }

    private static Media media(Long id, String fileId) {
        return new Media().setId(id).setFileId(fileId).setSource(MediaSource.OFFICIAL_TENCENT);
    }
}
