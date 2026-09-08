package com.tianji.exam.utils;

import com.tianji.api.dto.aigc.AiGeneratedQuestionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 题目内容校验工具测试
 */
class AiQuestionContentValidatorTest {

    /**
     * 验证合法单选题能够通过校验
     */
    @Test
    @DisplayName("合法单选题应通过校验")
    void shouldAcceptValidSingleChoiceQuestion() {
        AiGeneratedQuestionDTO question = question(1, List.of("A", "B", "C"), "1");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isTrue();
        assertThat(question.getAnswer()).isEqualTo("1");
    }

    /**
     * 验证单选题不能包含多个答案
     */
    @Test
    @DisplayName("单选题包含多个答案时应校验失败")
    void shouldRejectMultipleAnswersForSingleChoice() {
        AiGeneratedQuestionDTO question = question(1, List.of("A", "B", "C"), "0,1");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("单选题");
    }

    /**
     * 验证多选题答案能够按下标排序
     */
    @Test
    @DisplayName("多选题答案下标应排序并规范化")
    void shouldNormalizeMultipleChoiceAnswerOrder() {
        AiGeneratedQuestionDTO question = question(2, List.of("A", "B", "C"), "2, 0");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isTrue();
        assertThat(question.getAnswer()).isEqualTo("0,2");
    }

    /**
     * 验证选择题答案下标不能超出选项范围
     */
    @Test
    @DisplayName("选择题答案下标越界时应校验失败")
    void shouldRejectChoiceAnswerOutsideOptionRange() {
        AiGeneratedQuestionDTO question = question(2, List.of("A", "B"), "0,2");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("超出选项范围");
    }

    /**
     * 验证判断题仅接受约定的数字答案
     */
    @Test
    @DisplayName("判断题答案只能是 0 或 1")
    void shouldRejectInvalidTrueFalseAnswer() {
        AiGeneratedQuestionDTO question = question(4, List.of("正确", "错误"), "正确");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("0 或 1");
    }

    /**
     * 验证主观题会清理无意义选项
     */
    @Test
    @DisplayName("主观题应清除无意义的选项")
    void shouldRemoveOptionsFromSubjectiveQuestion() {
        AiGeneratedQuestionDTO question = question(5, new ArrayList<>(List.of("无意义选项")), "得分点一");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isTrue();
        assertThat(question.getOptions()).isEmpty();
    }

    /**
     * 验证过长解析不会进入正式题库
     */
    @Test
    @DisplayName("解析超过 300 个字符时应校验失败")
    void shouldRejectAnalysisLongerThanFormalQuestionLimit() {
        AiGeneratedQuestionDTO question = question(1, List.of("A", "B"), "0");
        question.setAnalysis("a".repeat(301));

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("300");
    }

    /**
     * 验证内容指纹会忽略标点、空格和大小写差异
     */
    @Test
    @DisplayName("标点、空格和大小写差异应生成相同指纹")
    void shouldGenerateSameFingerprintAfterTextNormalization() {
        String first = AiQuestionContentValidator.fingerprint(
                "Java 中，String 可变吗？", List.of("YES", "No"));
        String second = AiQuestionContentValidator.fingerprint(
                " java中 string 可变吗 ", List.of("yes", "no"));

        assertThat(first).isEqualTo(second);
    }

    /**
     * 验证不同选项内容会生成不同指纹
     */
    @Test
    @DisplayName("选项内容不同应生成不同指纹")
    void shouldGenerateDifferentFingerprintForDifferentOptions() {
        String first = AiQuestionContentValidator.fingerprint("以下哪项正确？", List.of("选项甲", "选项乙"));
        String second = AiQuestionContentValidator.fingerprint("以下哪项正确？", List.of("选项甲", "选项丙"));

        assertThat(first).isNotEqualTo(second);
    }

    /**
     * 验证选择题空选项不会被静默删除
     */
    @Test
    @DisplayName("选择题存在空选项时应校验失败")
    void shouldRejectBlankChoiceOptionInsteadOfRemovingIt() {
        AiGeneratedQuestionDTO question = question(1, List.of("A", "", "B"), "1");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("选项不能为空");
    }

    /**
     * 验证选择题 null 选项不会被静默删除
     */
    @Test
    @DisplayName("选择题存在 null 选项时应校验失败")
    void shouldRejectNullChoiceOptionInsteadOfRemovingIt() {
        AiGeneratedQuestionDTO question = question(1,
                new ArrayList<>(java.util.Arrays.asList("A", null, "B")), "1");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("选项不能为空");
    }

    /**
     * 验证选择题不能包含重复选项
     */
    @Test
    @DisplayName("选择题存在重复选项时应校验失败")
    void shouldRejectDuplicateChoiceOptions() {
        AiGeneratedQuestionDTO question = question(1, List.of("Java", " java ", "Python"), "1");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("重复选项");
    }

    /**
     * 验证选择题选项数量存在上限
     */
    @Test
    @DisplayName("选择题选项超过数量上限时应校验失败")
    void shouldRejectTooManyChoiceOptions() {
        AiGeneratedQuestionDTO question = question(1,
                new ArrayList<>(java.util.stream.IntStream.range(0, 11).mapToObj(String::valueOf).toList()), "1");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("不能超过 10");
    }

    /**
     * 验证单个选择题选项存在长度上限
     */
    @Test
    @DisplayName("单个选择题选项过长时应校验失败")
    void shouldRejectOverlongChoiceOption() {
        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(
                question(1, List.of("A".repeat(501), "B"), "1"));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("500");
    }

    /**
     * 验证主观题参考答案存在长度上限
     */
    @Test
    @DisplayName("主观题参考答案过长时应校验失败")
    void shouldRejectOverlongSubjectiveAnswer() {
        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(
                question(5, List.of(), "a".repeat(10001)));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("10000");
    }

    /**
     * 验证题目知识点数量存在上限
     */
    @Test
    @DisplayName("题目知识点超过数量上限时应校验失败")
    void shouldRejectTooManyKnowledgePoints() {
        AiGeneratedQuestionDTO question = question(1, List.of("A", "B"), "1");
        question.setKnowledgePoints(new ArrayList<>(java.util.stream.IntStream.range(0, 31)
                .mapToObj(index -> "知识点" + index).toList()));

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("不能超过 30");
    }

    /**
     * 验证单个知识点存在长度上限
     */
    @Test
    @DisplayName("单个知识点过长时应校验失败")
    void shouldRejectOverlongKnowledgePoint() {
        AiGeneratedQuestionDTO question = question(1, List.of("A", "B"), "1");
        question.setKnowledgePoints(List.of("a".repeat(101)));

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("100");
    }

    /**
     * 验证选择题答案中的连续逗号不会被忽略
     */
    @Test
    @DisplayName("选择题答案包含连续逗号时应校验失败")
    void shouldRejectChoiceAnswerWithConsecutiveCommas() {
        AiGeneratedQuestionDTO question = question(2, List.of("A", "B", "C"), "0,,1");

        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(question);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("非法下标");
    }

    /**
     * 验证选择题答案尾部逗号不会被忽略
     */
    @Test
    @DisplayName("选择题答案包含尾部逗号时应校验失败")
    void shouldRejectChoiceAnswerWithTrailingComma() {
        AiQuestionContentValidator.ValidationResult result = AiQuestionContentValidator.validate(
                question(2, List.of("A", "B", "C"), "0,1,"));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("非法下标");
    }
    /**
     * 验证相同题干和选项但题型不同的题目不会被误判为重复
     */
    @Test
    @DisplayName("题型不同的题目应具有不同内容指纹")
    void shouldKeepDifferentQuestionTypesDistinct() {
        String singleChoice = AiQuestionContentValidator.fingerprint(1, "以下哪项正确？", List.of("选项甲", "选项乙"));
        String judgment = AiQuestionContentValidator.fingerprint(4, "以下哪项正确？", List.of("选项甲", "选项乙"));

        assertThat(singleChoice).isNotEqualTo(judgment);
    }
    /**
     * 构造测试用 AI 题目
     *
     * @param type 题型
     * @param options 选项
     * @param answer 答案
     * @return 测试题目
     */
    private AiGeneratedQuestionDTO question(Integer type, List<String> options, String answer) {
        AiGeneratedQuestionDTO question = new AiGeneratedQuestionDTO();
        question.setName("示例题目");
        question.setType(type);
        question.setDifficulty(2);
        question.setScore(5);
        question.setOptions(options);
        question.setAnswer(answer);
        question.setAnalysis("示例解析");
        question.setKnowledgePoints(List.of("示例知识点"));
        return question;
    }
}