package com.tianji.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.exam.domain.po.AiQuestionDraft;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 题目草稿数据访问接口
 */
public interface AiQuestionDraftMapper extends BaseMapper<AiQuestionDraft> {

    /**
     * 更新草稿内容和重新校验后的状态。
     *
     * <p>JSON 字段必须显式指定类型处理器。不能通过 LambdaUpdateWrapper 直接绑定
     * {@link java.util.List}，否则 JDBC 会把集合按二进制对象传给 MySQL，导致
     * {@code Cannot create a JSON value from a string with CHARACTER SET 'binary'}。</p>
     *
     * @param draft 待更新的草稿内容
     * @return 受影响行数
     */
    @Update("""
            UPDATE ai_question_draft
            SET name = #{draft.name}, type = #{draft.type}, difficulty = #{draft.difficulty},
                score = #{draft.score},
                options = #{draft.options,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                answer = #{draft.answer}, analysis = #{draft.analysis},
                knowledge_points = #{draft.knowledgePoints,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                content_fingerprint = #{draft.contentFingerprint}, status = #{draft.status},
                validation_message = #{draft.validationMessage}, confirmed_by = NULL,
                confirmed_time = NULL, updater = #{draft.updater}, update_time = #{draft.updateTime}
            WHERE id = #{draft.id}
            """)
    int updateDraftContent(@Param("draft") AiQuestionDraft draft);

    /**
     * 统计指定批次已经保存的草稿数量。
     *
     * @param batchId 批次 ID
     * @return 草稿数量
     */
    @Select("SELECT COUNT(1) FROM ai_question_draft WHERE batch_id = #{batchId}")
    long countByBatchId(@Param("batchId") Long batchId);

    /**
     * 统计当前课程范围内的潜在重复题目数量。
     *
     * @param fingerprint 内容指纹
     * @param batchId 当前批次 ID
     * @param courseId 课程 ID
     * @param scopeType 出题范围类型
     * @param scopeId 出题范围 ID
     * @param targetBizId 目标练习目录 ID
     * @return 潜在重复题目数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM ai_question_draft d
            INNER JOIN ai_question_batch b ON b.id = d.batch_id
            WHERE d.content_fingerprint = #{fingerprint}
              AND d.batch_id <> #{batchId}
              AND d.status NOT IN ('REJECTED', 'INVALID')
              AND b.course_id = #{courseId}
              AND (
                    (b.scope_type = #{scopeType} AND b.scope_id = #{scopeId})
                    OR b.target_biz_id = #{targetBizId}
                  )
            """)
    long countPotentialDuplicate(@Param("fingerprint") String fingerprint,
                                 @Param("batchId") Long batchId,
                                 @Param("courseId") Long courseId,
                                 @Param("scopeType") String scopeType,
                                 @Param("scopeId") Long scopeId,
                                 @Param("targetBizId") Long targetBizId);
}
