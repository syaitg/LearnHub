package com.tianji.exam.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.exam.domain.dto.PracticeAnswerSaveDTO;
import com.tianji.exam.domain.dto.PracticeAiReviewConfirmDTO;
import com.tianji.exam.domain.dto.PracticeSessionCreateDTO;
import com.tianji.exam.domain.query.PracticeAiReviewPageQuery;
import com.tianji.exam.domain.query.PracticeSessionPageQuery;
import com.tianji.exam.domain.vo.PracticeAiReviewItemVO;
import com.tianji.exam.domain.vo.PracticeSessionVO;
import com.tianji.exam.service.IPracticeSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生练习会话相关接口
 */
@Tag(name = "学生练习会话相关接口")
@RestController
@RequestMapping("/practice-sessions")
@RequiredArgsConstructor
public class PracticeSessionController {

    private final IPracticeSessionService sessionService;

    /**
     * 创建练习会话
     *
     * @param dto 练习会话创建参数
     * @return 练习会话 ID
     */
    @Operation(summary = "创建练习会话")
    @PostMapping
    public Long createSession(@Valid @RequestBody PracticeSessionCreateDTO dto) {
        return sessionService.createSession(dto);
    }

    /**
     * 分页查询当前学生的练习记录
     *
     * @param query 分页查询参数
     * @return 练习会话分页结果
     */
    @Operation(summary = "分页查询我的练习记录")
    @GetMapping("/page")
    public PageDTO<PracticeSessionVO> queryMySessions(@Valid PracticeSessionPageQuery query) {
        return sessionService.queryMySessions(query);
    }

    /**
     * 分页查询课程创建者待审核的主观题 AI 评估结果
     *
     * @param query 待审核项查询参数
     * @return 待审核项分页结果
     */
    @Operation(summary = "分页查询主观题 AI 待审核项")
    @GetMapping("/ai-review/pending/page")
    public PageDTO<PracticeAiReviewItemVO> queryPendingAiReviews(@Valid PracticeAiReviewPageQuery query) {
        return sessionService.queryPendingAiReviews(query);
    }
    /**
     * 查询练习会话详情
     *
     * @param id 练习会话 ID
     * @return 练习会话详情
     */
    @Operation(summary = "查询练习会话详情")
    @GetMapping("/{id}")
    public PracticeSessionVO querySession(@PathVariable("id") Long id) {
        return sessionService.querySession(id);
    }

    /**
     * 保存指定练习题的答案
     *
     * @param sessionId 练习会话 ID
     * @param practiceQuestionId 练习题快照 ID
     * @param dto 答案保存参数
     */
    @Operation(summary = "保存练习题答案")
    @PutMapping("/{sessionId}/questions/{practiceQuestionId}/answer")
    public void saveAnswer(@PathVariable("sessionId") Long sessionId,
                           @PathVariable("practiceQuestionId") Long practiceQuestionId,
                           @Valid @RequestBody PracticeAnswerSaveDTO dto) {
        sessionService.saveAnswer(sessionId, practiceQuestionId, dto);
    }

    /**
     * 提交练习并触发自动判分
     *
     * @param id 练习会话 ID
     * @return 提交后的练习会话详情
     */
    @Operation(summary = "提交练习并自动判分")
    @PostMapping("/{id}/submit")
    public PracticeSessionVO submit(@PathVariable("id") Long id) {
        return sessionService.submit(id);
    }

    /**
     * 重试失败的主观题 AI 评估
     *
     * @param id 练习会话 ID
     */
    @Operation(summary = "重试主观题 AI 评估")
    @PostMapping("/{id}/ai-review/retry")
    public void retryAiReview(@PathVariable("id") Long id) {
        sessionService.retryAiReview(id);
    }

    /**
     * 由课程创建者确认主观题 AI 评估结果
     *
     * @param id 练习会话 ID
     * @param dto 人工确认参数
     */
    @Operation(summary = "人工确认主观题 AI 评估结果")
    @PostMapping("/{id}/ai-review/confirm")
    public void confirmAiReview(@PathVariable("id") Long id,
                                @Valid @RequestBody PracticeAiReviewConfirmDTO dto) {
        sessionService.confirmAiReview(id, dto);
    }
}

