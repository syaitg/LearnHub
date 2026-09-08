package com.tianji.aigc.domain.vo;

import com.tianji.aigc.domain.model.TranscriptSegment;
import com.tianji.aigc.domain.model.VideoKnowledgePoint;
import com.tianji.aigc.domain.model.VideoSectionSummary;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频 AI 处理任务展示信息
 */
@Data
public class VideoAiTaskVO {
    private Long id;
    private Long courseId;
    private Long sectionId;
    private String sectionName;
    private Long mediaId;
    private String mediaName;
    private Long mediaSize;
    private Long durationMs;
    private String transcriptionProvider;
    private String providerVersion;
    private String configVersion;
    private String language;
    private String status;
    private Integer progress;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String fullText;
    private List<TranscriptSegment> transcriptSegments;
    private String timestampMode;
    private String videoIntroduction;
    private String coreContent;
    private List<VideoSectionSummary> sectionSummaries;
    private List<String> keyConclusions;
    private List<String> suitableLearners;
    private List<String> reviewPoints;
    private List<VideoKnowledgePoint> knowledgePoints;
    private String resultVersion;
    private String failureReason;
    private LocalDateTime startedTime;
    private LocalDateTime transcribedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}