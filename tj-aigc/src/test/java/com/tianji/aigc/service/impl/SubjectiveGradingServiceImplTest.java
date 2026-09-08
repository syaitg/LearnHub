package com.tianji.aigc.service.impl;

import com.tianji.api.dto.aigc.SubjectiveGradingRequestDTO;
import com.tianji.api.dto.aigc.SubjectiveGradingResultDTO;
import com.tianji.common.exceptions.BizIllegalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 主观题 AI 评估结果边界校验测试
 */
class SubjectiveGradingServiceImplTest {

    private SubjectiveGradingServiceImpl gradingService;

    /**
     * 初始化主观题评估服务
     */
    @BeforeEach
    void setUp() {
        gradingService = new SubjectiveGradingServiceImpl(mock(ChatClient.class));
    }

    /**
     * 验证评估结果会规范化列表、补充总分和评分规则版本
     */
    @Test
    @DisplayName("评估结果应被规范化并自动识别低置信度复核")
    void shouldNormalizeResultAndRecommendManualReview() {
        SubjectiveGradingRequestDTO request = validRequest();
        request.setRubricVersion("  自定义规则-v2  ");
        SubjectiveGradingResultDTO result = validResult();
        result.setMatchedPoints(List.of(" 得分点一 ", "得分点一"));
        result.setMissingPoints(null);
        result.setImprovementSuggestion("  补充示例  ");
        result.setConfidence(new BigDecimal("0.69"));
        result.setManualReviewRecommended(false);

        validate(result, request);

        assertThat(result.getMatchedPoints()).containsExactly("得分点一");
        assertThat(result.getMissingPoints()).isEmpty();
        assertThat(result.getImprovementSuggestion()).isEqualTo("补充示例");
        assertThat(result.getTotalScore()).isEqualTo(10);
        assertThat(result.getRubricVersion()).isEqualTo("自定义规则-v2");
        assertThat(result.getManualReviewRecommended()).isTrue();
    }

    /**
     * 验证评估结果中的得分点数量超过上限时会被拒绝
     */
    @Test
    @DisplayName("得分点数量超过上限时应拒绝")
    void shouldRejectTooManyPointItems() {
        SubjectiveGradingResultDTO result = validResult();
        result.setMatchedPoints(java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> "得分点" + index)
                .toList());

        assertThatThrownBy(() -> validate(result, validRequest()))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("数量不能超过 50");
    }

    /**
     * 验证评估结果中的空得分点不会被保存
     */
    @Test
    @DisplayName("得分点为空时应拒绝")
    void shouldRejectBlankPointItem() {
        SubjectiveGradingResultDTO result = validResult();
        result.setMatchedPoints(List.of("得分点一", "  "));

        assertThatThrownBy(() -> validate(result, validRequest()))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("命中得分点不能为空");
    }

    /**
     * 验证单条得分点超过长度上限时会被拒绝
     */
    @Test
    @DisplayName("单条得分点过长时应拒绝")
    void shouldRejectTooLongPointItem() {
        SubjectiveGradingResultDTO result = validResult();
        result.setMatchedPoints(List.of("a".repeat(2001)));

        assertThatThrownBy(() -> validate(result, validRequest()))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("单条内容不能超过 2000");
    }

    /**
     * 验证改进建议超过长度上限时会被拒绝
     */
    @Test
    @DisplayName("改进建议过长时应拒绝")
    void shouldRejectTooLongSuggestion() {
        SubjectiveGradingResultDTO result = validResult();
        result.setImprovementSuggestion("a".repeat(5001));

        assertThatThrownBy(() -> validate(result, validRequest()))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("改进建议不能超过 5000");
    }

    /**
     * 验证建议分超出题目总分时会被拒绝
     */
    @Test
    @DisplayName("建议分越界时应拒绝")
    void shouldRejectScoreOutOfRange() {
        SubjectiveGradingResultDTO result = validResult();
        result.setSuggestedScore(11);

        assertThatThrownBy(() -> validate(result, validRequest()))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("建议分不合法");
    }

    /**
     * 验证置信度不在合法范围时会被拒绝
     */
    @Test
    @DisplayName("置信度越界时应拒绝")
    void shouldRejectConfidenceOutOfRange() {
        SubjectiveGradingResultDTO result = validResult();
        result.setConfidence(new BigDecimal("1.01"));

        assertThatThrownBy(() -> validate(result, validRequest()))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("置信度必须在 0 到 1 之间");
    }

    /**
     * 调用服务内部的统一结果校验逻辑
     *
     * @param result 待校验评估结果
     * @param request 原始评估请求
     */
    private void validate(SubjectiveGradingResultDTO result, SubjectiveGradingRequestDTO request) {
        ReflectionTestUtils.invokeMethod(gradingService, "normalizeRubricVersion", request);
        ReflectionTestUtils.invokeMethod(gradingService, "validateResult", result, request);
    }

    /**
     * 构造合法的主观题评估请求
     *
     * @return 主观题评估请求
     */
    private SubjectiveGradingRequestDTO validRequest() {
        SubjectiveGradingRequestDTO request = new SubjectiveGradingRequestDTO();
        request.setQuestionId(1L);
        request.setQuestion("请说明事务的 ACID 特性");
        request.setStandardAnswer("原子性、一致性、隔离性和持久性");
        request.setStudentAnswer("学生作答");
        request.setTotalScore(10);
        return request;
    }

    /**
     * 构造合法的主观题评估结果
     *
     * @return 主观题评估结果
     */
    private SubjectiveGradingResultDTO validResult() {
        SubjectiveGradingResultDTO result = new SubjectiveGradingResultDTO();
        result.setSuggestedScore(8);
        result.setMatchedPoints(List.of("得分点一"));
        result.setMissingPoints(List.of());
        result.setIncorrectStatements(List.of());
        result.setImprovementSuggestion("补充示例");
        result.setConfidence(new BigDecimal("0.85"));
        result.setManualReviewRecommended(false);
        return result;
    }
}