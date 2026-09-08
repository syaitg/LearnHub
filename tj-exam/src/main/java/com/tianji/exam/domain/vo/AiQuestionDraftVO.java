package com.tianji.exam.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
/**
 * AI 题目草稿视图数据
 */
@Data
public class AiQuestionDraftVO {
    private Long id;
    private Long batchId;
    private Integer sequenceNo;
    private String name;
    private Integer type;
    private Integer difficulty;
    private Integer score;
    private List<String> options;
    private String answer;
    private String analysis;
    private List<String> knowledgePoints;
    private String status;
    private String validationMessage;
    private Long questionId;
    private Long confirmedBy;
    private LocalDateTime confirmedTime;
    private LocalDateTime publishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
