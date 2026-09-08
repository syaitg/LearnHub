package com.tianji.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.aigc.domain.po.VideoAiTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频 AI 处理任务数据访问接口
 */
@Mapper
public interface VideoAiTaskMapper extends BaseMapper<VideoAiTask> {

    /**
     * 原子领取视频转写任务
     *
     * @param id 任务 ID
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET status = 'TRANSCRIBING', progress = 10, failure_reason = NULL,
                started_time = COALESCE(started_time, #{updateTime}), update_time = #{updateTime}
            WHERE id = #{id} AND status = 'CREATED' AND retry_count = #{expectedRetryCount}
            """)
    int startTranscription(@Param("id") Long id,
                           @Param("expectedRetryCount") int expectedRetryCount,
                           @Param("updateTime") LocalDateTime updateTime);

    /**
     * 保存视频转写结果并进入待分析状态
     *
     * @param id 任务 ID
     * @param provider 转写 Provider
     * @param providerVersion Provider 版本
     * @param language 识别语言
     * @param fullText 转写全文
     * @param segmentsJson 时间戳句段 JSON
     * @param timestampMode 时间戳来源模式
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET transcription_provider = #{provider}, provider_version = #{providerVersion},
                language = #{language}, full_text = #{fullText}, transcript_segments = #{segmentsJson},
                timestamp_mode = #{timestampMode}, status = 'TRANSCRIBED', progress = 55,
                transcribed_time = #{updateTime}, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'TRANSCRIBING' AND retry_count = #{expectedRetryCount}
            """)
    int saveTranscription(@Param("id") Long id,
                          @Param("provider") String provider,
                          @Param("providerVersion") String providerVersion,
                          @Param("language") String language,
                          @Param("fullText") String fullText,
                          @Param("segmentsJson") String segmentsJson,
                          @Param("timestampMode") String timestampMode,
                          @Param("expectedRetryCount") int expectedRetryCount,
                          @Param("updateTime") LocalDateTime updateTime);

    /**
     * 原子领取视频内容分析任务
     *
     * @param id 任务 ID
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET status = 'ANALYZING', progress = 70, failure_reason = NULL, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'TRANSCRIBED' AND retry_count = #{expectedRetryCount}
            """)
    int startAnalysis(@Param("id") Long id,
                      @Param("expectedRetryCount") int expectedRetryCount,
                      @Param("updateTime") LocalDateTime updateTime);

    /**
     * 保存视频摘要与知识点并完成任务
     *
     * @param id 任务 ID
     * @param introduction 视频简介
     * @param coreContent 核心内容
     * @param sectionSummariesJson 分段摘要 JSON
     * @param keyConclusionsJson 关键结论 JSON
     * @param suitableLearnersJson 适合学习者 JSON
     * @param reviewPointsJson 建议复习点 JSON
     * @param knowledgePointsJson 知识点 JSON
     * @param resultVersion 结果版本
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET video_introduction = #{introduction}, core_content = #{coreContent},
                section_summaries = #{sectionSummariesJson}, key_conclusions = #{keyConclusionsJson},
                suitable_learners = #{suitableLearnersJson}, review_points = #{reviewPointsJson},
                knowledge_points = #{knowledgePointsJson}, result_version = #{resultVersion},
                status = 'COMPLETED', progress = 100, failure_reason = NULL,
                completed_time = #{updateTime}, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'ANALYZING' AND retry_count = #{expectedRetryCount}
            """)
    int completeAnalysis(@Param("id") Long id,
                         @Param("introduction") String introduction,
                         @Param("coreContent") String coreContent,
                         @Param("sectionSummariesJson") String sectionSummariesJson,
                         @Param("keyConclusionsJson") String keyConclusionsJson,
                         @Param("suitableLearnersJson") String suitableLearnersJson,
                         @Param("reviewPointsJson") String reviewPointsJson,
                         @Param("knowledgePointsJson") String knowledgePointsJson,
                         @Param("resultVersion") String resultVersion,
                         @Param("expectedRetryCount") int expectedRetryCount,
                         @Param("updateTime") LocalDateTime updateTime);

    /**
     * 将正在执行的任务标记为失败
     *
     * @param id 任务 ID
     * @param failureReason 失败原因
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET status = 'FAILED', failure_reason = #{failureReason}, update_time = #{updateTime}
            WHERE id = #{id} AND retry_count = #{expectedRetryCount}
              AND status IN ('TRANSCRIBING', 'ANALYZING')
            """)
    int markFailed(@Param("id") Long id,
                   @Param("expectedRetryCount") int expectedRetryCount,
                   @Param("failureReason") String failureReason,
                   @Param("updateTime") LocalDateTime updateTime);

    /**
     * 刷新失败任务的媒资快照，确保重试时使用最新的临时授权地址。
     *
     * @param id 任务 ID
     * @param mediaName 媒资名称
     * @param mediaUrl 媒资访问地址
     * @param mediaSize 媒资大小
     * @param durationMs 媒资时长
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET media_name = #{mediaName}, media_url = #{mediaUrl}, media_size = #{mediaSize},
                duration_ms = #{durationMs}, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'FAILED'
            """)
    int refreshMediaSnapshot(@Param("id") Long id,
                             @Param("mediaName") String mediaName,
                             @Param("mediaUrl") String mediaUrl,
                             @Param("mediaSize") Long mediaSize,
                             @Param("durationMs") Long durationMs,
                             @Param("updateTime") LocalDateTime updateTime);

    /**
     * 将失败任务恢复到可执行状态并增加重试次数
     *
     * @param id 任务 ID
     * @param userId 创建者 ID
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET status = CASE WHEN full_text IS NULL OR full_text = '' THEN 'CREATED' ELSE 'TRANSCRIBED' END,
                progress = CASE WHEN full_text IS NULL OR full_text = '' THEN 0 ELSE 55 END,
                retry_count = retry_count + 1, failure_reason = NULL, update_time = #{updateTime}
            WHERE id = #{id} AND creater = #{userId} AND status = 'FAILED'
              AND retry_count < max_retry_count
            """)
    int resetForRetry(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询长时间未被执行的待恢复任务
     *
     * @param cutoffTime 恢复截止时间
     * @param limit 最大查询数量
     * @return 待重新投递的任务 ID
     */
    @Select("""
            SELECT id
            FROM video_ai_task
            WHERE status IN ('CREATED', 'TRANSCRIBED') AND update_time < #{cutoffTime}
            ORDER BY update_time ASC
            LIMIT #{limit}
            """)
    List<Long> selectPendingTaskIds(@Param("cutoffTime") LocalDateTime cutoffTime,
                                    @Param("limit") int limit);

    /**
     * 原子领取一个待恢复任务，避免多实例扫描重复投递同一任务
     *
     * @param id 任务 ID
     * @param cutoffTime 恢复截止时间
     * @param updateTime 更新时间
     * @return 是否成功领取
     */
    @Update("""
            UPDATE video_ai_task
            SET update_time = #{updateTime}
            WHERE id = #{id} AND status IN ('CREATED', 'TRANSCRIBED')
              AND update_time < #{cutoffTime}
            """)
    int claimPendingTask(@Param("id") Long id,
                         @Param("cutoffTime") LocalDateTime cutoffTime,
                         @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询长时间未完成的视频 AI 任务及其执行代际
     *
     * @param cutoffTime 超时截止时间
     * @param limit 最大查询数量
     * @return 超时任务
     */
    @Select("""
            SELECT id, retry_count
            FROM video_ai_task
            WHERE status IN ('TRANSCRIBING', 'ANALYZING') AND update_time < #{cutoffTime}
            ORDER BY update_time ASC
            LIMIT #{limit}
            """)
    List<VideoAiTask> selectTimedOutTasks(@Param("cutoffTime") LocalDateTime cutoffTime,
                                          @Param("limit") int limit);

    /**
     * 按任务 ID 和执行代际将超时任务标记为失败
     *
     * @param id 任务 ID
     * @param expectedRetryCount 本次执行对应的重试次数
     * @param cutoffTime 超时截止时间
     * @param updateTime 更新时间
     * @param failureReason 失败原因
     * @return 受影响行数
     */
    @Update("""
            UPDATE video_ai_task
            SET status = 'FAILED', failure_reason = #{failureReason}, update_time = #{updateTime}
            WHERE id = #{id} AND retry_count = #{expectedRetryCount}
              AND status IN ('TRANSCRIBING', 'ANALYZING') AND update_time < #{cutoffTime}
            """)
    int markTimedOut(@Param("id") Long id,
                     @Param("expectedRetryCount") int expectedRetryCount,
                     @Param("cutoffTime") LocalDateTime cutoffTime,
                     @Param("updateTime") LocalDateTime updateTime,
                     @Param("failureReason") String failureReason);

}
