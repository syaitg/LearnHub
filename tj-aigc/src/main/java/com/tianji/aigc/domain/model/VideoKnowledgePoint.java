package com.tianji.aigc.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 视频知识点分析结果
 */
@Data
@Accessors(chain = true)
public class VideoKnowledgePoint {
    private String name;
    private String description;
    private Integer importance;
    private Integer difficulty;
    private Long startMs;
    private Long endMs;
    private List<String> prerequisites;
}