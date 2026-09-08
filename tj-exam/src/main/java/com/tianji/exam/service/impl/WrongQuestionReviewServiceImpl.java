package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.exam.domain.po.WrongQuestionReview;
import com.tianji.exam.mapper.WrongQuestionReviewMapper;
import com.tianji.exam.service.IWrongQuestionReviewService;
import org.springframework.stereotype.Service;

/**
 * 错题重做记录服务实现
 */
@Service
public class WrongQuestionReviewServiceImpl extends ServiceImpl<WrongQuestionReviewMapper, WrongQuestionReview> implements IWrongQuestionReviewService {
}
