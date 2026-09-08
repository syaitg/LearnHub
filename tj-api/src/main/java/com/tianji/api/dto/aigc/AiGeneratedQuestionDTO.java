package com.tianji.api.dto.aigc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 生成题目数据
 */
@Data
@Schema(description = "AI 生成的题目")
public class AiGeneratedQuestionDTO {
    private String name;
    private Integer type;
    private Integer difficulty;
    private Integer score;
    private List<String> options;
    private String answer;
    private String analysis;
    private List<String> knowledgePoints;
}
