package com.tianji.aigc.service;

import com.tianji.api.dto.aigc.AiQuestionGenerateRequestDTO;
import com.tianji.api.dto.aigc.AiQuestionGenerateResultDTO;


/**
 * AI 智能出题服务
 */
public interface QuestionGenerationService {


    /**
     * 根据课程材料生成题目
     *
     * @param request AI 出题请求
     * @return AI 生成结果
     */
    AiQuestionGenerateResultDTO generate(AiQuestionGenerateRequestDTO request);
}
