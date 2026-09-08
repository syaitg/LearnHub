package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
/**
 * 错题重做记录实体
 */
@Data
@Accessors(chain = true)
@TableName("wrong_question_review")
public class WrongQuestionReview {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long wrongQuestionId;
    private Long userId;
    private String studentAnswer;
    private Boolean correct;
    private Integer score;
    private LocalDateTime reviewTime;
}
