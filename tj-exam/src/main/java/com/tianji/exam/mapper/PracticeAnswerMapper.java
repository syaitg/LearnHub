package com.tianji.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.query.PracticeAiReviewPageQuery;
import com.tianji.exam.domain.vo.PracticeAiReviewItemVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 练习答案数据访问接口。
 */
public interface PracticeAnswerMapper extends BaseMapper<PracticeAnswer> {

    /**
     * 分页查询等待课程创建者确认的主观题 AI 批改记录。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 待确认记录分页结果
     */
    Page<PracticeAiReviewItemVO> selectPendingAiReviewPage(Page<PracticeAiReviewItemVO> page,
                                                            @Param("query") PracticeAiReviewPageQuery query);

    /**
     * 查询 AI 批改状态长期未更新的练习会话 ID。
     *
     * @param cutoff 超时截止时间
     * @param limit 最大查询数量
     * @return 练习会话 ID 列表
     */
    @Select("SELECT DISTINCT session_id FROM practice_answer "
            + "WHERE status = 'AI_REVIEWING' AND update_time < #{cutoff} "
            + "ORDER BY session_id ASC LIMIT #{limit}")
    List<Long> selectStaleReviewingSessionIds(@Param("cutoff") LocalDateTime cutoff,
                                              @Param("limit") int limit);

    /**
     * 统计指定练习会话中尚未完成 AI 批改的答案数量。
     *
     * @param sessionId 练习会话 ID
     * @return 尚未完成 AI 批改的答案数量
     */
    @Select("SELECT COUNT(*) FROM practice_answer WHERE session_id = #{sessionId} "
            + "AND status IN ('PENDING_AI_REVIEW', 'AI_REVIEWING')")
    int countUnfinishedAiReview(@Param("sessionId") Long sessionId);

    /**
     * 统计已生成 AI 批改建议但等待人工确认的答案数量。
     *
     * @param sessionId 练习会话 ID
     * @return 等待人工确认的答案数量
     */
    @Select("SELECT COUNT(*) FROM practice_answer WHERE session_id = #{sessionId} "
            + "AND status = 'AI_REVIEWED'")
    int countAiReviewed(@Param("sessionId") Long sessionId);

    /**
     * 统计 AI 批改失败且尚未形成正式分数的答案数量。
     *
     * @param sessionId 练习会话 ID
     * @return AI 批改失败的答案数量
     */
    @Select("SELECT COUNT(*) FROM practice_answer WHERE session_id = #{sessionId} "
            + "AND status = 'AI_REVIEW_FAILED'")
    int countAiReviewFailed(@Param("sessionId") Long sessionId);

    /**
     * 汇总指定练习会话中已经形成的正式得分。
     *
     * @param sessionId 练习会话 ID
     * @return 当前正式总分
     */
    @Select("SELECT COALESCE(SUM(score), 0) FROM practice_answer WHERE session_id = #{sessionId} "
            + "AND score IS NOT NULL")
    int sumScoredAnswers(@Param("sessionId") Long sessionId);
}
