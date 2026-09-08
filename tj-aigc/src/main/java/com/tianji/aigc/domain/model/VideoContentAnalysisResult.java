package com.tianji.aigc.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 视频摘要与知识点分析结果
 */
@Data
@Accessors(chain = true)
public class VideoContentAnalysisResult {
    private String introduction;
    private String coreContent;
    private List<VideoSectionSummary> sectionSummaries;
    private List<String> keyConclusions;
    private List<String> suitableLearners;
    private List<String> reviewPoints;
    private List<VideoKnowledgePoint> knowledgePoints;
}