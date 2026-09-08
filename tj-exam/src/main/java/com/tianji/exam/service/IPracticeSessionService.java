package com.tianji.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.exam.domain.dto.PracticeAnswerSaveDTO;
import com.tianji.exam.domain.dto.PracticeAiReviewConfirmDTO;
import com.tianji.exam.domain.dto.PracticeSessionCreateDTO;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.domain.query.PracticeAiReviewPageQuery;
import com.tianji.exam.domain.query.PracticeSessionPageQuery;
import com.tianji.exam.domain.vo.PracticeAiReviewItemVO;
import com.tianji.exam.domain.vo.PracticeSessionVO;

/**
 * 练习会话服务
 */
public interface IPracticeSessionService extends IService<PracticeSession> {

    /**
     * 创建练习会话
     */
    Long createSession(PracticeSessionCreateDTO dto);

    /**
     * 查询练习会话详情
     */
    PracticeSessionVO querySession(Long id);

    /**
     * 分页查询当前用户的练习会话
     */
    PageDTO<PracticeSessionVO> queryMySessions(PracticeSessionPageQuery query);

    /**
     * 分页查询课程创建者待审核的主观题 AI 评估结果
     *
     * @param query 待审核项查询参数
     * @return 待审核项分页结果
     */
    PageDTO<PracticeAiReviewItemVO> queryPendingAiReviews(PracticeAiReviewPageQuery query);

    /**
     * 保存练习题答案
     */
    void saveAnswer(Long sessionId, Long practiceQuestionId, PracticeAnswerSaveDTO dto);

    /**
     * 提交练习会话
     */
    PracticeSessionVO submit(Long sessionId);

    /**
     * 重试主观题 AI 评估
     */
    void retryAiReview(Long sessionId);

    /**
     * 由课程创建者确认主观题 AI 评估结果
     *
     * @param sessionId 练习会话 ID
     * @param dto 人工确认参数
     */
    void confirmAiReview(Long sessionId, PracticeAiReviewConfirmDTO dto);
}
