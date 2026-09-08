package com.tianji.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.exam.domain.po.PracticeSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 练习会话数据访问接口
 */
public interface PracticeSessionMapper extends BaseMapper<PracticeSession> {

    /**
     * 根据主键锁定练习会话
     *
     * @param id 练习会话 ID
     * @return 被锁定的练习会话 ID
     */
    @Select("SELECT id FROM practice_session WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    /**
     * 忽略幂等唯一键冲突并创建练习会话
     *
     * @param session 练习会话
     * @return 影响行数
     */
    @Insert("""
            INSERT IGNORE INTO practice_session (
                id, user_id, course_id, target_biz_id, target_name, status,
                total_questions, answered_count, objective_count, subjective_count,
                total_score, objective_full_score, objective_score, final_score, correct_count,
                ai_review_pending_count, ai_review_retry_count, ai_review_max_retry_count,
                request_id, create_time, update_time
            ) VALUES (
                #{id}, #{userId}, #{courseId}, #{targetBizId}, #{targetName}, #{status},
                #{totalQuestions}, #{answeredCount}, #{objectiveCount}, #{subjectiveCount},
                #{totalScore}, #{objectiveFullScore}, #{objectiveScore}, #{finalScore}, #{correctCount},
                #{aiReviewPendingCount}, #{aiReviewRetryCount}, #{aiReviewMaxRetryCount},
                #{requestId}, #{createTime}, #{updateTime}
            )
            """)
    int insertIgnore(PracticeSession session);

    /**
     * 查询长时间停留在已提交且仍有待评估答案的练习会话。
     *
     * @param cutoff 更新时间截止点
     * @param limit 单次最多查询数量
     * @return 待恢复的练习会话
     */
    @Select("SELECT id, user_id, ai_review_retry_count FROM practice_session "
            + "WHERE status = 'SUBMITTED' AND ai_review_pending_count > 0 "
            + "AND update_time < #{cutoff} ORDER BY update_time ASC LIMIT #{limit}")
    List<PracticeSession> selectStaleSubmittedSessions(@Param("cutoff") LocalDateTime cutoff,
                                                       @Param("limit") int limit);

    /**
     * 原子领取一个待恢复的主观题评估会话，避免多实例重复投递。
     *
     * @param id 练习会话 ID
     * @param expectedRetryCount 本次执行对应的 AI 评估重试次数
     * @param cutoff 更新时间截止点
     * @param updateTime 更新时间
     * @return 是否成功领取
     */
    @Update("UPDATE practice_session SET update_time = #{updateTime} "
            + "WHERE id = #{id} AND status = 'SUBMITTED' AND ai_review_pending_count > 0 "
            + "AND ai_review_retry_count = #{expectedRetryCount} AND update_time < #{cutoff}")
    int claimStaleSubmittedSession(@Param("id") Long id,
                                   @Param("expectedRetryCount") int expectedRetryCount,
                                   @Param("cutoff") LocalDateTime cutoff,
                                   @Param("updateTime") LocalDateTime updateTime);
}
