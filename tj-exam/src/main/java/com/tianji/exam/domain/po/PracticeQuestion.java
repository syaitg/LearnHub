package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 练习题目快照实体
 */
@Data
@Accessors(chain = true)
@TableName(value = "practice_question", autoResultMap = true)
public class PracticeQuestion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long sessionId;
    private Long questionId;
    private Integer sequenceNo;
    private String name;
    private Integer type;
    private Integer difficulty;
    private Integer score;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;
    private String standardAnswer;
    private String analysis;
    private String gradingMethod;
    private LocalDateTime createTime;
}
