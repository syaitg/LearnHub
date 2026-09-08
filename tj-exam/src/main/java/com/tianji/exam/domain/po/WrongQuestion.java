package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
/**
 * 学生错题实体
 */
@Data
@Accessors(chain = true)
@TableName("wrong_question")
public class WrongQuestion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long questionId;
    private Long courseId;
    private Long targetBizId;
    private Long latestPracticeQuestionId;
    private Long latestAnswerId;
    private Integer wrongCount;
    private Integer reviewCount;
    private String status;
    private LocalDateTime lastWrongTime;
    private LocalDateTime lastReviewTime;
    private LocalDateTime masteredTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
