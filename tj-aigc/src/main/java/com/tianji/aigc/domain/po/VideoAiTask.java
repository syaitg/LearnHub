package com.tianji.aigc.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.tianji.aigc.domain.model.TranscriptSegment;
import com.tianji.aigc.domain.model.VideoKnowledgePoint;
import com.tianji.aigc.domain.model.VideoSectionSummary;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频 AI 处理任务实体
 */
@Data
@Accessors(chain = true)
@TableName(value = "video_ai_task", autoResultMap = true)
public class VideoAiTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long courseId;
    private Long sectionId;
    private String sectionName;
    private Long mediaId;
    private String mediaName;
    private String mediaUrl;
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

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<TranscriptSegment> transcriptSegments;

    private String timestampMode;
    private String videoIntroduction;
    private String coreContent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<VideoSectionSummary> sectionSummaries;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keyConclusions;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> suitableLearners;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> reviewPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<VideoKnowledgePoint> knowledgePoints;

    private String resultVersion;
    private String failureReason;
    private String requestId;
    private LocalDateTime startedTime;
    private LocalDateTime transcribedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long creater;
    private Long updater;
}