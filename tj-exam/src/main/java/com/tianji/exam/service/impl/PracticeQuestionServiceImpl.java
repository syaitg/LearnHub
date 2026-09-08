package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.exam.domain.po.PracticeQuestion;
import com.tianji.exam.mapper.PracticeQuestionMapper;
import com.tianji.exam.service.IPracticeQuestionService;
import org.springframework.stereotype.Service;

/**
 * 练习题目服务实现
 */
@Service
public class PracticeQuestionServiceImpl extends ServiceImpl<PracticeQuestionMapper, PracticeQuestion> implements IPracticeQuestionService {
}
