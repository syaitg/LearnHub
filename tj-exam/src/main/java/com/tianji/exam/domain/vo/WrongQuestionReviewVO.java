package com.tianji.exam.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题重做记录
 */
@Data
@Schema(description = "错题重做记录")
public class WrongQuestionReviewVO {

    private Long id;
    private String studentAnswer;
    private Boolean correct;
    private Integer score;
    private LocalDateTime reviewTime;
}
