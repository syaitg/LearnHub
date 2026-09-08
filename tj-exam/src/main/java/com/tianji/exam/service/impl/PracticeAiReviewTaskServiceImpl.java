package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.api.client.aigc.AigcTaskClient;
import com.tianji.api.dto.aigc.SubjectiveGradingRequestDTO;
import com.tianji.api.dto.aigc.SubjectiveGradingResultDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.po.PracticeQuestion;
import com.tianji.exam.mapper.PracticeQuestionMapper;
import com.tianji.exam.service.PracticeAiReviewPersistenceService;
import com.tianji.exam.service.PracticeAiReviewTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主观题 AI 异步评估任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeAiReviewTaskServiceImpl implements PracticeAiReviewTaskService {

    private static final int FAILURE_REASON_MAX_LENGTH = 1000;
    private static final int MAX_REVIEW_LIST_ITEMS = 50;
    private static final int MAX_REVIEW_ITEM_LENGTH = 2000;
    private static final int MAX_IMPROVEMENT_SUGGESTION_LENGTH = 5000;
    private static final String DEFAULT_RUBRIC_VERSION = "subjective-rubric-v1";

    private final PracticeAiReviewPersistenceService persistenceService;
    private final PracticeQuestionMapper questionMapper;
    private final AigcTaskClient aigcTaskClient;

    /**
     * 在异步任务边界领取并评估指定执行代际的主观题答案
     *
     * @param sessionId 练习会话 ID
     * @param userId 学生用户 ID
     * @param expectedRetryCount 本次任务对应的 AI 评估重试次数
     */
    @Override
    @Async
    public void reviewAsync(Long sessionId, Long userId, int expectedRetryCount) {
        List<PracticeAnswer> claimedAnswers = List.of();
        UserContext.setUser(userId);
        try {
            claimedAnswers = persistenceService.claimPendingAnswers(sessionId, expectedRetryCount);
            if (claimedAnswers.isEmpty()) {
                return;
            }
            Map<Long, PracticeQuestion> questionMap = queryQuestionMap(sessionId, claimedAnswers);
            for (PracticeAnswer answer : claimedAnswers) {
                PracticeQuestion question = questionMap.get(answer.getPracticeQuestionId());
                if (question == null) {
                    throw new IllegalStateException("练习题快照不存在，无法执行 AI 评估");
                }
                SubjectiveGradingResultDTO result = aigcTaskClient.gradeSubjective(toRequest(question, answer));
                applyResult(answer, question, result);
            }
            persistenceService.completeReview(sessionId, expectedRetryCount, claimedAnswers);
        } catch (Exception e) {
            log.error("练习会话 {} 的主观题 AI 评估失败", sessionId, e);
            if (!claimedAnswers.isEmpty()) {
                persistenceService.failReview(sessionId, expectedRetryCount, normalizeFailureReason(e));
            }
        } finally {
            UserContext.removeUser();
        }
    }

    /**
     * 查询本次待评估答案对应的练习题快照
     *
     * @param sessionId 练习会话 ID
     * @param answers 待评估答案
     * @return 练习题快照映射
     */
    private Map<Long, PracticeQuestion> queryQuestionMap(Long sessionId, List<PracticeAnswer> answers) {
        List<Long> ids = answers.stream().map(PracticeAnswer::getPracticeQuestionId).toList();
        return questionMapper.selectList(Wrappers.<PracticeQuestion>lambdaQuery()
                        .eq(PracticeQuestion::getSessionId, sessionId)
                        .in(PracticeQuestion::getId, ids))
                .stream()
                .collect(Collectors.toMap(PracticeQuestion::getId, Function.identity()));
    }

    /**
     * 组装主观题 AI 评估请求
     *
     * @param question 练习题快照
     * @param answer 学生答案
     * @return 主观题评估请求
     */
    private SubjectiveGradingRequestDTO toRequest(PracticeQuestion question, PracticeAnswer answer) {
        SubjectiveGradingRequestDTO request = new SubjectiveGradingRequestDTO();
        request.setQuestionId(question.getQuestionId());
        request.setQuestion(question.getName());
        request.setStandardAnswer(question.getStandardAnswer());
        request.setAnalysis(question.getAnalysis());
        request.setStudentAnswer(answer.getStudentAnswer());
        request.setTotalScore(question.getScore());
        request.setRubricVersion(DEFAULT_RUBRIC_VERSION);
        return request;
    }

    /**
     * 校验并填充 AIGC 返回的主观题评估结果
     *
     * @param answer 学生答案
     * @param question 练习题快照
     * @param result AI 评估结果
     */
    private void applyResult(PracticeAnswer answer, PracticeQuestion question, SubjectiveGradingResultDTO result) {
        if (result == null || result.getSuggestedScore() == null
                || result.getSuggestedScore() < 0
                || result.getSuggestedScore() > question.getScore()) {
            throw new IllegalStateException("AI 评估结果中的建议分无效");
        }
        if (result.getConfidence() == null
                || result.getConfidence().compareTo(BigDecimal.ZERO) < 0
                || result.getConfidence().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("AI 评估结果中的置信度无效");
        }
        answer.setAiSuggestedScore(result.getSuggestedScore())
                .setAiTotalScore(question.getScore())
                .setMatchedPoints(normalizeReviewList(result.getMatchedPoints(), "命中得分点"))
                .setMissingPoints(normalizeReviewList(result.getMissingPoints(), "缺失得分点"))
                .setIncorrectStatements(normalizeReviewList(result.getIncorrectStatements(), "错误表述"))
                .setImprovementSuggestion(normalizeSuggestion(result.getImprovementSuggestion()))
                .setConfidence(result.getConfidence())
                .setManualReviewRecommended(Boolean.TRUE.equals(result.getManualReviewRecommended()))
                .setRubricVersion(DEFAULT_RUBRIC_VERSION);
    }

    /**
     * 校验并规范化 AI 返回的评估列表
     *
     * @param values 原始列表
     * @param fieldName 字段名称
     * @return 非空且长度受控的列表
     */
    private List<String> normalizeReviewList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_REVIEW_LIST_ITEMS) {
            throw new IllegalStateException("AI 评估结果中的" + fieldName + "数量超过限制");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("AI 评估结果中的" + fieldName + "不能为空");
            }
            String item = value.trim();
            if (item.length() > MAX_REVIEW_ITEM_LENGTH) {
                throw new IllegalStateException("AI 评估结果中的" + fieldName + "单条内容超过长度限制");
            }
            if (!normalized.contains(item)) {
                normalized.add(item);
            }
        }
        return normalized;
    }

    /**
     * 校验并规范化 AI 返回的改进建议
     *
     * @param suggestion 原始改进建议
     * @return 规范化后的改进建议
     */
    private String normalizeSuggestion(String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return "";
        }
        String normalized = suggestion.trim();
        if (normalized.length() > MAX_IMPROVEMENT_SUGGESTION_LENGTH) {
            throw new IllegalStateException("AI 评估结果中的改进建议超过长度限制");
        }
        return normalized;
    }

    /**
     * 规范化并截断异步任务失败原因
     *
     * @param exception 任务异常
     * @return 可持久化的失败原因
     */
    private String normalizeFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "主观题 AI 评估执行失败";
        }
        String normalized = message.trim();
        return normalized.length() <= FAILURE_REASON_MAX_LENGTH
                ? normalized
                : normalized.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}