package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
/**
 * 练习会话实体
 */
@Data
@Accessors(chain = true)
@TableName("practice_session")
public class PracticeSession {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long courseId;
    private Long targetBizId;
    private String targetName;
    private String status;
    private Integer totalQuestions;
    private Integer answeredCount;
    private Integer objectiveCount;
    private Integer subjectiveCount;
    private Integer totalScore;
    private Integer objectiveFullScore;
    private Integer objectiveScore;
    private Integer finalScore;
    private Integer correctCount;
    private Integer aiReviewPendingCount;
    private Integer aiReviewRetryCount;
    private Integer aiReviewMaxRetryCount;
    private String requestId;
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
