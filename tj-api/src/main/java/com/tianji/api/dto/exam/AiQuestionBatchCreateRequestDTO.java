package com.tianji.api.dto.exam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 跨服务创建 AI 出题草稿批次的请求参数
 */
@Data
public class AiQuestionBatchCreateRequestDTO {

    @NotNull
    private Long courseId;

    @NotBlank
    private String scopeType;

    @NotNull
    private Long scopeId;

    @Size(max = 50)
    private List<@NotNull Long> scopeSectionIds;

    @NotBlank
    private String sourceType;

    @Size(max = 128)
    private String sourceId;

    @Size(max = 128)
    private String sourceVersion;

    @NotNull
    private Long targetBizId;

    @Size(max = 30)
    private List<@Size(max = 100) String> knowledgePoints;

    @Size(max = 20000)
    private String materialText;

    @NotEmpty
    private List<Integer> questionTypes;

    @NotNull
    @Min(1)
    @Max(20)
    private Integer questionCount;

    @NotNull
    @Min(1)
    @Max(3)
    private Integer difficulty;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer score;

    @Size(max = 64)
    private String requestId;
}