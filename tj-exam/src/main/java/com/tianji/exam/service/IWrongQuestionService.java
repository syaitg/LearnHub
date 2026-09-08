package com.tianji.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.exam.domain.dto.WrongQuestionReviewDTO;
import com.tianji.exam.domain.po.WrongQuestion;
import com.tianji.exam.domain.query.WrongQuestionPageQuery;
import com.tianji.exam.domain.vo.WrongQuestionReviewVO;
import com.tianji.exam.domain.vo.WrongQuestionVO;

import java.util.List;

/**
 * 学生错题服务
 */
public interface IWrongQuestionService extends IService<WrongQuestion> {

    /**
     * 分页查询当前用户的错题
     */
    PageDTO<WrongQuestionVO> queryMyWrongQuestions(WrongQuestionPageQuery query);

    /**
     * 提交一次错题重做
     */
    WrongQuestionReviewVO review(Long id, WrongQuestionReviewDTO dto);

    /**
     * 查询错题重做记录
     */
    List<WrongQuestionReviewVO> queryReviews(Long id);

    /**
     * 记录或更新学生错题
     */
    void recordWrongQuestion(Long userId, Long courseId, Long targetBizId,
                             Long practiceQuestionId, Long questionId, Long answerId);
}
