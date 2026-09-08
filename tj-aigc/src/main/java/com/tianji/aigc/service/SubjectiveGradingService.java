package com.tianji.aigc.service;

import com.tianji.api.dto.aigc.SubjectiveGradingRequestDTO;
import com.tianji.api.dto.aigc.SubjectiveGradingResultDTO;


/**
 * 主观题 AI 评估服务
 */
public interface SubjectiveGradingService {


    /**
     * 生成主观题评估建议
     *
     * @param request 主观题评估请求
     * @return 主观题评估结果
     */
    SubjectiveGradingResultDTO grade(SubjectiveGradingRequestDTO request);
}
