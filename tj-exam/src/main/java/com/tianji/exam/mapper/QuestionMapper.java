package com.tianji.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.exam.domain.po.Question;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 题目数据访问接口
 *
 * @author Sy
 * @since 2026-09-02
 */
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 统计指定创建人的题目数量
     *
     * @param createrIds 创建人 ID 集合
     * @return 创建人及题目数量集合
     */
    List<IdAndNumDTO> countQuestionOfCreater(@Param("createrIds") List<Long> createrIds);

    /**
     * 原子累加题目作答次数和正确次数
     *
     * @param questionId 题目 ID
     * @param correct 本次作答是否正确
     * @return 影响行数
     */
    @Update("""
            UPDATE question
            SET answer_times = COALESCE(answer_times, 0) + 1,
                correct_times = COALESCE(correct_times, 0) + CASE WHEN #{correct} THEN 1 ELSE 0 END
            WHERE id = #{questionId}
            """)
    int incrementAnswerStatistics(@Param("questionId") Long questionId,
                                  @Param("correct") boolean correct);
}
