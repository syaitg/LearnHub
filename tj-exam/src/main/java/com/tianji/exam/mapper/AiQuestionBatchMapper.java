package com.tianji.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.exam.domain.po.AiQuestionBatch;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 出题批次数据访问接口
 */
public interface AiQuestionBatchMapper extends BaseMapper<AiQuestionBatch> {

    /**
     * 插入 AI 出题批次
     *
     * @param batch 批次实体
     * @return 受影响行数，插入成功为 1
     */
    @Insert("""
            INSERT IGNORE INTO ai_question_batch (
                id, course_id, course_name, scope_type, scope_id, scope_name,
                scope_section_ids, scope_section_names,
                source_type, source_id, source_version, target_biz_id,
                knowledge_points, material_text, question_types, question_count,
                difficulty, score, cate_id1, cate_id2, cate_id3, status,
                total_count, valid_count, duplicate_count, published_count,
                failure_reason, retry_count, max_retry_count, request_id,
                request_fingerprint, create_time, update_time, creater, updater
            ) VALUES (
                #{batch.id}, #{batch.courseId}, #{batch.courseName}, #{batch.scopeType},
                #{batch.scopeId}, #{batch.scopeName},
                #{batch.scopeSectionIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                #{batch.scopeSectionNames,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                #{batch.sourceType}, #{batch.sourceId}, #{batch.sourceVersion}, #{batch.targetBizId},
                #{batch.knowledgePoints,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                #{batch.materialText},
                #{batch.questionTypes,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                #{batch.questionCount}, #{batch.difficulty}, #{batch.score},
                #{batch.cateId1}, #{batch.cateId2}, #{batch.cateId3}, #{batch.status},
                #{batch.totalCount}, #{batch.validCount}, #{batch.duplicateCount},
                #{batch.publishedCount}, #{batch.failureReason}, #{batch.retryCount},
                #{batch.maxRetryCount}, #{batch.requestId}, #{batch.requestFingerprint},
                #{batch.createTime}, #{batch.updateTime}, #{batch.creater}, #{batch.updater}
            )
            """)
    int insertIgnore(@Param("batch") AiQuestionBatch batch);

    /**
     * 锁定批次，避免确认、发布等操作并发执行
     * 该方法必须在事务中调用
     *
     * @param id 批次 ID
     * @return 批次 ID
     */
    @Select("SELECT id FROM ai_question_batch WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    /**
     * 将创建状态的批次置为生成中
     *
     * @param id 批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE ai_question_batch
            SET status = 'GENERATING', failure_reason = NULL, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'CREATED' AND retry_count = #{expectedRetryCount}
            """)
    int startGeneration(@Param("id") Long id,
                        @Param("expectedRetryCount") int expectedRetryCount,
                        @Param("updateTime") LocalDateTime updateTime);

    /**
     * 将生成中的批次置为校验中
     *
     * @param id 批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE ai_question_batch
            SET status = 'VALIDATING', update_time = #{updateTime}
            WHERE id = #{id} AND status = 'GENERATING' AND retry_count = #{expectedRetryCount}
            """)
    int startValidation(@Param("id") Long id,
                        @Param("expectedRetryCount") int expectedRetryCount,
                        @Param("updateTime") LocalDateTime updateTime);

    /**
     * 保存题目校验统计并更新批次状态
     *
     * @param id 批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param status 批次状态
     * @param totalCount 题目总数
     * @param validCount 有效题目数
     * @param duplicateCount 重复题目数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE ai_question_batch
            SET status = #{status}, total_count = #{totalCount}, valid_count = #{validCount},
                duplicate_count = #{duplicateCount}, published_count = 0,
                failure_reason = NULL, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'VALIDATING' AND retry_count = #{expectedRetryCount}
            """)
    int completeValidation(@Param("id") Long id,
                           @Param("expectedRetryCount") int expectedRetryCount,
                           @Param("status") String status,
                           @Param("totalCount") int totalCount,
                           @Param("validCount") int validCount,
                           @Param("duplicateCount") int duplicateCount,
                           @Param("updateTime") LocalDateTime updateTime);

    /**
     * 将生成过程中的批次标记为失败
     *
     * @param id 批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param failureReason 失败原因
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE ai_question_batch
            SET status = 'GENERATION_FAILED', failure_reason = #{failureReason}, update_time = #{updateTime}
            WHERE id = #{id} AND status IN ('GENERATING', 'VALIDATING')
              AND retry_count = #{expectedRetryCount}
            """)
    int markGenerationFailed(@Param("id") Long id,
                             @Param("expectedRetryCount") int expectedRetryCount,
                             @Param("failureReason") String failureReason,
                             @Param("updateTime") LocalDateTime updateTime);

    /**
     * 将生成失败的批次恢复为创建状态并增加重试次数
     *
     * @param id 批次 ID
     * @param userId 创建者 ID
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE ai_question_batch
            SET status = 'CREATED', failure_reason = NULL,
                retry_count = retry_count + 1, updater = #{userId}, update_time = #{updateTime}
            WHERE id = #{id}
              AND creater = #{userId}
              AND status = 'GENERATION_FAILED'
              AND retry_count < max_retry_count
            """)
    int resetForRetry(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询长时间停留在创建状态的批次及其执行代际。
     *
     * @param cutoff 更新时间截止点
     * @param limit 单次最多查询数量
     * @return 待恢复的批次
     */
    @Select("SELECT id, retry_count FROM ai_question_batch WHERE status = 'CREATED' "
            + "AND update_time < #{cutoff} ORDER BY update_time ASC LIMIT #{limit}")
    List<AiQuestionBatch> selectStaleCreatedTasks(@Param("cutoff") LocalDateTime cutoff,
                                                  @Param("limit") int limit);

    /**
     * 原子领取一个待恢复的 AI 出题批次，避免多实例重复投递。
     *
     * @param id 批次 ID
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param cutoff 更新时间截止点
     * @param updateTime 更新时间
     * @return 是否成功领取
     */
    @Update("UPDATE ai_question_batch SET update_time = #{updateTime} "
            + "WHERE id = #{id} AND status = 'CREATED' AND retry_count = #{expectedRetryCount} "
            + "AND update_time < #{cutoff}")
    int claimStaleCreatedTask(@Param("id") Long id,
                              @Param("expectedRetryCount") int expectedRetryCount,
                              @Param("cutoff") LocalDateTime cutoff,
                              @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询长时间停留在生成或校验状态的 AI 出题批次及其执行代际。
     *
     * @param cutoff 更新时间截止点
     * @param limit 单次最多查询数量
     * @return 超时批次
     */
    @Select("SELECT id, retry_count FROM ai_question_batch "
            + "WHERE status IN ('GENERATING', 'VALIDATING') AND update_time < #{cutoff} "
            + "ORDER BY update_time ASC LIMIT #{limit}")
    List<AiQuestionBatch> selectStaleProcessingTasks(@Param("cutoff") LocalDateTime cutoff,
                                                     @Param("limit") int limit);

    /**
     * 按批次 ID、执行代际和截止时间将超时批次标记为生成失败。
     *
     * @param id 批次 ID
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param cutoff 更新时间截止点
     * @param failureReason 失败原因
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("UPDATE ai_question_batch SET status = 'GENERATION_FAILED', "
            + "failure_reason = #{failureReason}, update_time = #{updateTime} "
            + "WHERE id = #{id} AND retry_count = #{expectedRetryCount} "
            + "AND status IN ('GENERATING', 'VALIDATING') AND update_time < #{cutoff}")
    int markStaleProcessingFailed(@Param("id") Long id,
                                  @Param("expectedRetryCount") int expectedRetryCount,
                                  @Param("cutoff") LocalDateTime cutoff,
                                  @Param("failureReason") String failureReason,
                                  @Param("updateTime") LocalDateTime updateTime);
}
