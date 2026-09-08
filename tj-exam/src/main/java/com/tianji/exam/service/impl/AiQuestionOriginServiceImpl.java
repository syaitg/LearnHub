package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.exam.domain.po.AiQuestionOrigin;
import com.tianji.exam.mapper.AiQuestionOriginMapper;
import com.tianji.exam.service.IAiQuestionOriginService;
import org.springframework.stereotype.Service;

/**
 * AI 题目来源服务实现
 */
@Service
public class AiQuestionOriginServiceImpl extends ServiceImpl<AiQuestionOriginMapper, AiQuestionOrigin>
        implements IAiQuestionOriginService {
}
