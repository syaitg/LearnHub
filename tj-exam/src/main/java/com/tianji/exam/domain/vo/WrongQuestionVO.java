package com.tianji.exam.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生错题详情
 */
@Data
@Schema(description = "学生错题详情")
public class WrongQuestionVO {

    private Long id;
    private Long questionId;
    private Long courseId;
    private Long targetBizId;
    private String name;
    private Integer type;
    private Integer difficulty;
    private Integer score;
    private List<String> options;
    private String standardAnswer;
    private String analysis;
    private Integer wrongCount;
    private Integer reviewCount;
    private String status;
    private LocalDateTime lastWrongTime;
    private LocalDateTime lastReviewTime;
    private LocalDateTime masteredTime;
}
