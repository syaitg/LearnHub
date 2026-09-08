package com.tianji.api.client.aigc;

import com.tianji.api.dto.aigc.AiQuestionGenerateRequestDTO;
import com.tianji.api.dto.aigc.AiQuestionGenerateResultDTO;
import com.tianji.api.dto.aigc.SubjectiveGradingRequestDTO;
import com.tianji.api.dto.aigc.SubjectiveGradingResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AIGC 异步任务远程调用接口
 *
 * <p>智能出题和主观题评估不存在安全的替代结果，因此不配置降级实现。
 * 调用异常由任务执行边界捕获，并回写对应任务的失败状态。</p>
 */
@FeignClient(value = "aigc-service", contextId = "aigcTask")
public interface AigcTaskClient {

    /**
     * 生成 AI 题目
     *
     * @param request AI 出题请求
     * @return AI 生成结果
     */
    @PostMapping("/ai/questions/generate")
    AiQuestionGenerateResultDTO generateQuestions(@RequestBody AiQuestionGenerateRequestDTO request);

    /**
     * 生成主观题 AI 评估建议
     *
     * @param request 主观题评估请求
     * @return 主观题评估结果
     */
    @PostMapping("/ai/subjective-grading")
    SubjectiveGradingResultDTO gradeSubjective(@RequestBody SubjectiveGradingRequestDTO request);
}
