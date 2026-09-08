package com.tianji.media.task;

import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.ConfirmEventsRequest;
import com.tencentcloudapi.vod.v20180717.models.EventContent;
import com.tencentcloudapi.vod.v20180717.models.FileUploadTask;
import com.tencentcloudapi.vod.v20180717.models.MediaBasicInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaMetaData;
import com.tencentcloudapi.vod.v20180717.models.PullEventsResponse;
import com.tianji.media.config.TencentProperties;
import com.tianji.media.domain.po.Media;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.service.IMediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PullEventTaskTest {

    @Mock
    private VodClient officialVodClient;
    @Mock
    private VodClient ownVodClient;
    @Mock
    private IMediaService mediaService;

    @Test
    void 自有账号上传事件应保存为自有媒资并只确认自有账号事件() throws Exception {
        TencentProperties properties = properties(false, true);
        PullEventTask task = new PullEventTask(
                officialVodClient, ownVodClient, mediaService, properties);
        EventContent event = uploadEvent("own-event-handle", "own-file-id");
        PullEventsResponse response = new PullEventsResponse();
        response.setEventSet(new EventContent[]{event});
        when(ownVodClient.PullEvents(any())).thenReturn(response);

        task.pullEvent();

        ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
        verify(mediaService).updateMediaProcedureResult(mediaCaptor.capture());
        assertThat(mediaCaptor.getValue().getSource()).isEqualTo(MediaSource.OWN_TENCENT);
        ArgumentCaptor<ConfirmEventsRequest> requestCaptor =
                ArgumentCaptor.forClass(ConfirmEventsRequest.class);
        verify(ownVodClient).ConfirmEvents(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEventHandles())
                .containsExactly("own-event-handle");
        verify(officialVodClient, never()).PullEvents(any());
    }

    @Test
    void 官方账号上传事件应保存为官方媒资并只确认官方账号事件() throws Exception {
        TencentProperties properties = properties(true, false);
        PullEventTask task = new PullEventTask(
                officialVodClient, ownVodClient, mediaService, properties);
        EventContent event = uploadEvent("official-event-handle", "official-file-id");
        PullEventsResponse response = new PullEventsResponse();
        response.setEventSet(new EventContent[]{event});
        when(officialVodClient.PullEvents(any())).thenReturn(response);

        task.pullEvent();

        ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
        verify(mediaService).updateMediaProcedureResult(mediaCaptor.capture());
        assertThat(mediaCaptor.getValue().getSource()).isEqualTo(MediaSource.OFFICIAL_TENCENT);
        ArgumentCaptor<ConfirmEventsRequest> requestCaptor =
                ArgumentCaptor.forClass(ConfirmEventsRequest.class);
        verify(officialVodClient).ConfirmEvents(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEventHandles())
                .containsExactly("official-event-handle");
        verify(ownVodClient, never()).PullEvents(any());
    }

    private static TencentProperties properties(boolean officialEnabled, boolean ownEnabled) {
        TencentProperties properties = new TencentProperties();
        properties.setVod(vodProperties(officialEnabled));
        properties.setOwnVod(vodProperties(ownEnabled));
        return properties;
    }

    private static TencentProperties.VodProperties vodProperties(boolean enabled) {
        TencentProperties.VodProperties vod = new TencentProperties.VodProperties();
        TencentProperties.EventPullProperties eventPull = new TencentProperties.EventPullProperties();
        eventPull.setEnabled(enabled);
        vod.setEventPull(eventPull);
        return vod;
    }

    private static EventContent uploadEvent(String eventHandle, String fileId) {
        MediaMetaData metadata = new MediaMetaData();
        metadata.setSize(1024L);
        metadata.setDuration(12.5F);
        MediaBasicInfo basicInfo = new MediaBasicInfo();
        basicInfo.setName("测试视频.mp4");
        basicInfo.setMediaUrl("https://example.com/video.mp4");
        basicInfo.setCoverUrl("https://example.com/cover.jpg");
        FileUploadTask uploadTask = new FileUploadTask();
        uploadTask.setFileId(fileId);
        uploadTask.setMetaData(metadata);
        uploadTask.setMediaBasicInfo(basicInfo);
        EventContent event = new EventContent();
        event.setEventHandle(eventHandle);
        event.setEventType("NewFileUpload");
        event.setFileUploadEvent(uploadTask);
        return event;
    }
}
