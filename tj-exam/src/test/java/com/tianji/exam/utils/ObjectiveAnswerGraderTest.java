package com.tianji.exam.utils;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.exam.constants.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 客观题确定性判分工具测试
 */
class ObjectiveAnswerGraderTest {

    /**
     * 验证单选题答案完全匹配时获得满分
     */
    @Test
    @DisplayName("单选题答案正确时应获得满分")
    void shouldGiveFullScoreForCorrectRadioAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.RADIO.getValue(), "1", "1", 5);

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.correct()).isTrue();
    }

    /**
     * 验证单选题答案错误时得零分
     */
    @Test
    @DisplayName("单选题答案错误时应得零分")
    void shouldGiveZeroForWrongRadioAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.RADIO.getValue(), "1", "2", 5);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证单选题标准答案不能包含多个选项下标
     */
    @Test
    @DisplayName("单选题标准答案包含多个下标时应判错")
    void shouldRejectMultipleIndexesInRadioStandardAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.RADIO.getValue(), "0,1", "0,1", 5);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证单选题学生答案不能包含多个选项下标
     */
    @Test
    @DisplayName("单选题学生答案包含多个下标时应判错")
    void shouldRejectMultipleIndexesInRadioStudentAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.RADIO.getValue(), "1", "0,1", 5);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证学生答案尾部逗号不会被误判为正确
     */
    @Test
    @DisplayName("学生答案包含尾部逗号时应判错")
    void shouldRejectTrailingCommaInStudentAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.RADIO.getValue(), "1", "1,", 5);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }
    /**
     * 验证多选题答案顺序不影响判分
     */
    @Test
    @DisplayName("多选题答案顺序不同时仍应判为正确")
    void shouldIgnoreOrderForMultiAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.MULTI.getValue(), "0,2,3", "3, 0, 2", 8);

        assertThat(result.score()).isEqualTo(8);
        assertThat(result.correct()).isTrue();
    }

    /**
     * 验证多选题缺少正确选项时不得分
     */
    @Test
    @DisplayName("多选题缺选时应得零分")
    void shouldGiveZeroForIncompleteMultiAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.MULTI.getValue(), "0,2,3", "0,2", 8);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证判断题支持常见中文答案
     */
    @Test
    @DisplayName("判断题应支持中文答案")
    void shouldSupportChineseJudgeAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.JUDGE.getValue(), "1", "正确", 2);

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.correct()).isTrue();
    }


    /**
     * 验证普通判分不会自动兼容历史判断题数字答案
     */
    @Test
    @DisplayName("普通判分不应自动兼容历史判断题数字答案")
    void shouldNotSupportLegacyNumericJudgeAnswerByDefault() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.JUDGE.getValue(), "2", "0", 2);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证阶段练习兼容历史判断题“1 表示正确，其他数字表示错误”的约定
     */
    @Test
    @DisplayName("阶段练习应兼容历史判断题数字答案")
    void shouldSupportLegacyNumericJudgeAnswerForPractice() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.gradeWithLegacyChoiceCompatibility(
                QuestionType.JUDGE.getValue(), "2", "0", 2);

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.correct()).isTrue();
    }

    /**
     * 验证不定项选择题全部选对时获得满分
     */
    @Test
    @DisplayName("不定项选择题全部选对时应获得满分")
    void shouldGiveFullScoreForCompleteUncertaintyAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.UNCERTAINTY.getValue(), "0,2,3", "3,2,0", 9);

        assertThat(result.score()).isEqualTo(9);
        assertThat(result.correct()).isTrue();
    }

    /**
     * 验证不定项选择题只选部分正确选项时按比例向下取整
     */
    @Test
    @DisplayName("不定项选择题部分正确时应按比例得分")
    void shouldGivePartialScoreForUncertaintyAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.UNCERTAINTY.getValue(), "0,2,3", "0,3", 8);

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证不定项选择题选中错误选项时得零分
     */
    @Test
    @DisplayName("不定项选择题包含错误选项时应得零分")
    void shouldGiveZeroWhenUncertaintyAnswerContainsWrongOption() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.UNCERTAINTY.getValue(), "0,2,3", "0,1", 8);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证非法格式的学生答案不会被误判为正确
     */
    @Test
    @DisplayName("非法选择题答案格式应判为错误")
    void shouldRejectMalformedChoiceAnswer() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.RADIO.getValue(), "1", "A", 5);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证普通判分不会自动把旧题库的 1-based 单选答案转换为 0-based
     */
    @Test
    @DisplayName("普通判分不应自动兼容旧题库单选答案")
    void shouldNotSupportLegacyOneBasedRadioAnswerByDefault() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.RADIO.getValue(), "1", "0", 5);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证阶段练习可以兼容旧题库的 1-based 单选标准答案
     */
    @Test
    @DisplayName("阶段练习应兼容旧题库单选答案")
    void shouldSupportLegacyOneBasedRadioAnswerForPractice() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.gradeWithLegacyChoiceCompatibility(
                QuestionType.RADIO.getValue(), "1", "0", 5);

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.correct()).isTrue();
    }

    /**
     * 验证普通判分不会自动把旧题库的 1-based 多选答案转换为 0-based
     */
    @Test
    @DisplayName("普通判分不应自动兼容旧题库多选答案")
    void shouldNotSupportLegacyOneBasedMultiAnswerByDefault() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                QuestionType.MULTI.getValue(), "1,2,3", "0,1,2", 8);

        assertThat(result.score()).isZero();
        assertThat(result.correct()).isFalse();
    }

    /**
     * 验证阶段练习可以兼容旧题库的 1-based 多选标准答案
     */
    @Test
    @DisplayName("阶段练习应兼容旧题库多选答案")
    void shouldSupportLegacyOneBasedMultiAnswerForPractice() {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.gradeWithLegacyChoiceCompatibility(
                QuestionType.MULTI.getValue(), "1,2,3", "0,1,2", 8);

        assertThat(result.score()).isEqualTo(8);
        assertThat(result.correct()).isTrue();
    }

    /**
     * 验证主观题不能使用客观题规则判分
     */
    @Test
    @DisplayName("主观题调用规则判分时应拒绝")
    void shouldRejectSubjectiveQuestion() {
        assertThatThrownBy(() -> ObjectiveAnswerGrader.grade(
                QuestionType.SUBJECTIVE.getValue(), "参考答案", "学生答案", 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不支持");
    }
}