package com.tianji.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.exam.domain.po.WrongQuestion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学生错题数据访问接口
 */
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {

    /**
     * 根据主键锁定错题记录
     *
     * @param id 错题记录 ID
     * @return 被锁定的错题记录 ID
     */
    @Select("SELECT id FROM wrong_question WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    /**
     * 根据学生、课程、目录和题目维度新增或累加错题记录
     *
     * @param wrongQuestion 错题记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO wrong_question (
                id, user_id, question_id, course_id, target_biz_id,
                latest_practice_question_id, latest_answer_id, wrong_count, review_count,
                status, last_wrong_time, create_time, update_time
            ) VALUES (
                #{id}, #{userId}, #{questionId}, #{courseId}, #{targetBizId},
                #{latestPracticeQuestionId}, #{latestAnswerId}, 1, 0,
                'ACTIVE', #{lastWrongTime}, #{createTime}, #{updateTime}
            )
            ON DUPLICATE KEY UPDATE
                latest_practice_question_id = VALUES(latest_practice_question_id),
                latest_answer_id = VALUES(latest_answer_id),
                wrong_count = wrong_count + 1,
                status = 'ACTIVE',
                mastered_time = NULL,
                last_wrong_time = VALUES(last_wrong_time),
                update_time = VALUES(update_time)
            """)
    int upsertWrongQuestion(WrongQuestion wrongQuestion);
}
