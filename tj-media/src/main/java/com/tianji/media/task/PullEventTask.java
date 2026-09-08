package com.tianji.media.task;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.ConfirmEventsRequest;
import com.tencentcloudapi.vod.v20180717.models.EventContent;
import com.tencentcloudapi.vod.v20180717.models.FileUploadTask;
import com.tencentcloudapi.vod.v20180717.models.MediaBasicInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaMetaData;
import com.tencentcloudapi.vod.v20180717.models.MediaProcessTaskCoverBySnapshotResult;
import com.tencentcloudapi.vod.v20180717.models.MediaProcessTaskResult;
import com.tencentcloudapi.vod.v20180717.models.PullEventsRequest;
import com.tencentcloudapi.vod.v20180717.models.PullEventsResponse;
import com.tencentcloudapi.vod.v20180717.models.ProcedureTask;
import com.tencentcloudapi.vod.v20180717.models.CoverBySnapshotTaskOutput;
import com.tianji.media.config.TencentProperties;
import com.tianji.media.domain.po.Media;
import com.tianji.media.enums.FileStatus;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.service.IMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 分别拉取黑马官方和自有腾讯云 VOD 账号的事件，避免两个账号的媒资来源互相污染。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
public class PullEventTask {

    private static final String PROCEDURE_EVENT = "ProcedureStateChanged";
    private static final String UPLOAD_EVENT = "NewFileUpload";
    private static final String PROCEDURE_EVENT_FINISH = "FINISH";

    private final VodClient officialVodClient;
    private final VodClient ownVodClient;
    private final IMediaService mediaService;
    private final TencentProperties tencentProperties;

    public PullEventTask(@Qualifier("tencentVodClient") VodClient officialVodClient,
                         @Qualifier("ownTencentVodClient") VodClient ownVodClient,
                         IMediaService mediaService,
                         TencentProperties tencentProperties) {
        this.officialVodClient = officialVodClient;
        this.ownVodClient = ownVodClient;
        this.mediaService = mediaService;
        this.tencentProperties = tencentProperties;
    }

    /**
     * 按固定间隔分别轮询两个 VOD 账号。每个账号独立确认事件，避免一个账号的事件被另一个账号确认。
     */
    @Scheduled(fixedDelayString = "${tj.tencent.vod.event-pull.fixed-delay:10000}")
    public void pullEvent() {
        if (isEventPullEnabled(MediaSource.OFFICIAL_TENCENT)) {
            pullEvent(MediaSource.OFFICIAL_TENCENT, officialVodClient);
        }
        if (isEventPullEnabled(MediaSource.OWN_TENCENT)) {
            pullEvent(MediaSource.OWN_TENCENT, ownVodClient);
        }
    }

    /** 拉取指定来源账号的事件，并只确认该账号已经成功处理的事件。 */
    private void pullEvent(MediaSource source, VodClient vodClient) {
        if (vodClient == null) {
            log.error("{} VOD 事件轮询未配置客户端", source.getDesc());
            return;
        }
        try {
            log.debug("开始拉取{} VOD 事件", source.getDesc());
            PullEventsResponse response = vodClient.PullEvents(new PullEventsRequest());
            if (response == null || response.getEventSet() == null
                    || response.getEventSet().length == 0) {
                log.debug("{} VOD 没有待处理事件", source.getDesc());
                return;
            }

            List<String> eventHandles = new ArrayList<>();
            for (EventContent event : response.getEventSet()) {
                if (event == null) {
                    log.warn("{} VOD 返回了空事件，忽略处理", source.getDesc());
                    continue;
                }
                try {
                    handleEvent(event, source);
                    if (event.getEventHandle() != null && !event.getEventHandle().isBlank()) {
                        eventHandles.add(event.getEventHandle());
                    } else {
                        log.warn("{} VOD 事件处理成功但缺少事件句柄，事件类型={}",
                                source.getDesc(), event.getEventType());
                    }
                } catch (Exception e) {
                    // 处理失败的事件不确认，让腾讯云保留事件并在下一轮重试。
                    log.error("处理{} VOD 事件失败，事件类型={}，事件句柄={}",
                            source.getDesc(), event.getEventType(), event.getEventHandle(), e);
                }
            }

            if (eventHandles.isEmpty()) {
                log.warn("{} VOD 没有可以确认的已成功处理事件", source.getDesc());
                return;
            }
            ConfirmEventsRequest confirmRequest = new ConfirmEventsRequest();
            confirmRequest.setEventHandles(eventHandles.toArray(new String[0]));
            vodClient.ConfirmEvents(confirmRequest);
            log.info("{} VOD 已处理并确认 {} 个事件", source.getDesc(), eventHandles.size());
        } catch (TencentCloudSDKException e) {
            if (isNoEventException(e)) {
                log.debug("{} VOD 当前没有待处理事件", source.getDesc());
            } else {
                log.error("{} VOD 事件轮询失败", source.getDesc(), e);
            }
        }
    }

    /** 根据来源账号分发事件处理逻辑。 */
    private void handleEvent(EventContent event, MediaSource source) {
        String eventType = event.getEventType();
        if (PROCEDURE_EVENT.equals(eventType)) {
            handleProcedureStateChangeEvent(event, source);
        } else if (UPLOAD_EVENT.equals(eventType)) {
            handleUploadEvent(event, source);
        } else {
            // 不支持的事件也确认，避免无关事件阻塞同一账号的事件队列。
            log.debug("确认不支持的{} VOD 事件类型={}", source.getDesc(), eventType);
        }
    }

    private boolean isEventPullEnabled(MediaSource source) {
        TencentProperties.VodProperties vod = source == MediaSource.OWN_TENCENT
                ? tencentProperties.getOwnVod()
                : tencentProperties.getVod();
        return vod != null && vod.getEventPull() != null && vod.getEventPull().isEnabled();
    }

    private boolean isNoEventException(TencentCloudSDKException exception) {
        return containsNoEvent(exception.getErrorCode()) || containsNoEvent(exception.getMessage());
    }

    private boolean containsNoEvent(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("no event");
    }

    private void handleUploadEvent(EventContent event, MediaSource source) {
        FileUploadTask uploadTask = event.getFileUploadEvent();
        if (uploadTask == null || uploadTask.getFileId() == null
                || uploadTask.getFileId().isBlank()) {
            throw new IllegalArgumentException("VOD 上传事件缺少文件信息");
        }
        MediaMetaData metadata = uploadTask.getMetaData();
        MediaBasicInfo basicInfo = uploadTask.getMediaBasicInfo();
        if (metadata == null || basicInfo == null) {
            throw new IllegalArgumentException("VOD 上传事件缺少完整媒资信息");
        }

        Media media = new Media();
        media.setFileId(uploadTask.getFileId());
        media.setSource(source);
        media.setFilename(basicInfo.getName());
        media.setMediaUrl(basicInfo.getMediaUrl());
        media.setCoverUrl(basicInfo.getCoverUrl());
        media.setDuration(metadata.getDuration());
        media.setSize(metadata.getSize());
        media.setStatus(FileStatus.UPLOADED);
        mediaService.updateMediaProcedureResult(media);
    }

    private void handleProcedureStateChangeEvent(EventContent event, MediaSource source) {
        ProcedureTask procedureTask = event.getProcedureStateChangeEvent();
        if (procedureTask == null) {
            throw new IllegalArgumentException("VOD 任务流事件缺少任务信息");
        }
        if (!PROCEDURE_EVENT_FINISH.equals(procedureTask.getStatus())) {
            return;
        }
        if (procedureTask.getFileId() == null || procedureTask.getFileId().isBlank()
                || procedureTask.getMetaData() == null) {
            throw new IllegalArgumentException("VOD 任务流事件缺少完整媒资信息");
        }

        MediaProcessTaskResult[] resultSet = procedureTask.getMediaProcessResultSet();
        Optional<MediaProcessTaskResult> coverResult = resultSet == null
                ? Optional.empty()
                : Arrays.stream(resultSet)
                .filter(result -> result != null && "CoverBySnapshot".equals(result.getType()))
                .findFirst();
        String coverUrl = coverResult
                .map(MediaProcessTaskResult::getCoverBySnapshotTask)
                .map(MediaProcessTaskCoverBySnapshotResult::getOutput)
                .map(CoverBySnapshotTaskOutput::getCoverUrl)
                .orElse(null);

        Media media = new Media();
        media.setFileId(procedureTask.getFileId());
        media.setSource(source);
        media.setFilename(procedureTask.getFileName());
        media.setMediaUrl(procedureTask.getFileUrl());
        media.setCoverUrl(coverUrl);
        media.setDuration(procedureTask.getMetaData().getDuration());
        media.setSize(procedureTask.getMetaData().getSize());
        media.setStatus(FileStatus.PROCESSED);
        mediaService.updateMediaProcedureResult(media);
    }
}
