package com.tianji.api.client.exam;

import com.tianji.api.dto.exam.AiQuestionBatchCreateRequestDTO;
import com.tianji.api.dto.exam.QuestionBizDTO;
import com.tianji.api.dto.exam.QuestionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 考试服务远程调用接口
 */
@FeignClient(value = "exam-service", contextId = "exam")
public interface ExamClient {

    /**
     * 批量保存题目业务关系
     *
     * @param qbs 题目业务关系列表
     */
    @PostMapping("/question-biz/list")
    void saveQuestionBizInfoBatch(@RequestBody Iterable<QuestionBizDTO> qbs);

    /**
     * 根据业务 ID 查询题目业务关系
     *
     * @param bizIds 业务 ID 集合
     * @return 题目业务关系列表
     */
    @GetMapping("/question-biz/biz/list")
    List<QuestionBizDTO> queryQuestionIdsByBizIds(@RequestParam("ids") Iterable<Long> bizIds);

    /**
     * 根据业务 ID 查询题目总分
     *
     * @param bizIds 业务 ID 集合
     * @return 业务 ID 与总分映射
     */
    @GetMapping("/question-biz/scores")
    Map<Long, Integer> queryQuestionScoresByBizIds(@RequestParam("ids") Iterable<Long> bizIds);

    /**
     * 按业务目录批量清理题目关系
     *
     * @param bizIds 业务目录 ID 集合
     */
    @DeleteMapping("/question-biz/biz")
    void deleteQuestionBizInfoByBizIds(@RequestParam("ids") List<Long> bizIds);

    /**
     * 根据题目 ID 查询题目
     *
     * @param ids 题目 ID 集合
     * @return 题目列表
     */
    @GetMapping("/questions/list")
    List<QuestionDTO> queryQuestionByIds(@RequestParam("ids") Iterable<Long> ids);

    /**
     * 统计教师录入题目数量
     *
     * @param createrIds 创建者 ID 集合
     * @return 创建者 ID 与题目数量映射
     */
    @GetMapping("/questions/numOfTeacher")
    Map<Long, Integer> countSubjectNumOfTeacher(@RequestParam("ids") Iterable<Long> createrIds);

    /**
     * 查询指定题目的分值
     *
     * @param ids 题目 ID 集合
     * @return 题目 ID 与分值映射
     */
    @GetMapping("/questions//scores")
    Map<Long, Integer> queryQuestionScores(@RequestParam("ids") Iterable<Long> ids);

    /**
     * 创建 AI 出题草稿批次
     *
     * @param request 创建请求
     * @return 出题批次 ID
     */
    @PostMapping("/ai-question-batches/internal")
    Long createAiQuestionBatch(@RequestBody AiQuestionBatchCreateRequestDTO request);
}