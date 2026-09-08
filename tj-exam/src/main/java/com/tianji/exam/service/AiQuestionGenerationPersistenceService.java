package com.tianji.exam.service;

import com.tianji.api.dto.aigc.AiGeneratedQuestionDTO;

import java.util.List;

/**
 * AI 题目生成结果持久化服务
 */
public interface AiQuestionGenerationPersistenceService {

    /**
     * 校验并保存模型生成的题目草稿。
     *
     * @param batchId 出题批次 ID
     * @param expectedRetryCount 本次执行对应的预期重试次数
     * @param generated 模型生成的题目列表
     */
    void persist(Long batchId, int expectedRetryCount, List<AiGeneratedQuestionDTO> generated);
}