package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.service.IPracticeAnswerService;
import org.springframework.stereotype.Service;

/**
 * 练习答案服务实现
 */
@Service
public class PracticeAnswerServiceImpl extends ServiceImpl<PracticeAnswerMapper, PracticeAnswer> implements IPracticeAnswerService {
}
