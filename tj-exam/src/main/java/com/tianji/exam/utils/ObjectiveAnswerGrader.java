package com.tianji.exam.utils;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.exam.constants.QuestionType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 客观题确定性判分工具
 */
public final class ObjectiveAnswerGrader {

    /**
     * 禁止实例化客观题判分工具
     */
    private ObjectiveAnswerGrader() {
    }

    /**
     * 判断题型是否支持规则判分
     *
     * @param questionType 题型值
     * @return 是否支持规则判分
     */
    public static boolean supports(Integer questionType) {
        QuestionType type = QuestionType.of(questionType);
        return type != null && type != QuestionType.SUBJECTIVE;
    }

    /**
     * 根据题型、标准答案和学生答案执行确定性判分。
     *
     * <p>普通业务统一使用当前答案协议：选择题答案使用 0-based 下标，判断题答案使用 0/1
     * 或常见中文、英文表达。这里不自动兼容历史题库答案，避免影响错题重做等已有判分流程。</p>
     *
     * @param questionType 题型值
     * @param standardAnswer 标准答案
     * @param studentAnswer 学生答案
     * @param totalScore 题目总分
     * @return 判分结果
     */
    public static GradeResult grade(Integer questionType, String standardAnswer,
                                    String studentAnswer, Integer totalScore) {
        return gradeInternal(questionType, standardAnswer, studentAnswer, totalScore, false);
    }

    /**
     * 阶段练习专用判分入口。
     *
     * <p>历史题库中同时存在两种答案约定：选择题标准答案曾使用 1-based 选项序号，
     * 判断题曾约定 1 表示正确、其他数字表示错误；学生端当前统一提交 0-based 选择题下标和 0/1
     * 判断答案。阶段练习需要兼容历史数据，因此单独使用此入口，不能替代普通判分入口。</p>
     *
     * @param questionType 题型值
     * @param standardAnswer 标准答案
     * @param studentAnswer 学生答案
     * @param totalScore 题目总分
     * @return 判分结果
     */
    public static GradeResult gradeWithLegacyChoiceCompatibility(Integer questionType,
                                                                  String standardAnswer,
                                                                  String studentAnswer,
                                                                  Integer totalScore) {
        return gradeInternal(questionType, standardAnswer, studentAnswer, totalScore, true);
    }

    /**
     * 执行客观题判分。
     *
     * @param questionType 题型值
     * @param standardAnswer 标准答案
     * @param studentAnswer 学生答案
     * @param totalScore 题目总分
     * @param legacyCompatible 是否兼容历史题库答案
     * @return 判分结果
     */
    private static GradeResult gradeInternal(Integer questionType, String standardAnswer,
                                             String studentAnswer, Integer totalScore,
                                             boolean legacyCompatible) {
        if (!supports(questionType)) {
            throw new BadRequestException("当前题型不支持客观题规则判分");
        }
        if (totalScore == null || totalScore <= 0) {
            throw new BadRequestException("题目分值必须大于 0");
        }
        QuestionType type = QuestionType.of(questionType);
        return switch (type) {
            case RADIO -> gradeChoice(standardAnswer, studentAnswer, totalScore, true, legacyCompatible);
            case MULTI -> gradeChoice(standardAnswer, studentAnswer, totalScore, false, legacyCompatible);
            case UNCERTAINTY -> gradeUncertainty(standardAnswer, studentAnswer, totalScore, legacyCompatible);
            case JUDGE -> gradeExact(normalizeJudgeAnswer(standardAnswer, legacyCompatible),
                    normalizeJudgeAnswer(studentAnswer, legacyCompatible), totalScore);
            default -> throw new BadRequestException("当前题型不支持客观题规则判分");
        };
    }

    /**
     * 对单选题或多选题进行完全匹配判分
     *
     * @param standardAnswer 标准答案
     * @param studentAnswer 学生答案
     * @param totalScore 题目总分
     * @param single 是否为单选题
     * @param legacyCompatible 是否兼容历史题库的 1-based 答案
     * @return 判分结果
     */
    private static GradeResult gradeChoice(String standardAnswer, String studentAnswer,
                                           int totalScore, boolean single, boolean legacyCompatible) {
        Set<Integer> standard = parseChoiceAnswer(standardAnswer);
        Set<Integer> selected = parseChoiceAnswer(studentAnswer);
        if (standard.isEmpty() || selected.isEmpty()) {
            return new GradeResult(0, false);
        }
        if (single && (standard.size() != 1 || selected.size() != 1)) {
            return new GradeResult(0, false);
        }
        boolean correct = standard.equals(selected);
        if (!correct && legacyCompatible && !standard.contains(0)) {
            // 只有阶段练习显式开启兼容时，才把历史 1-based 标准答案转换为 0-based。
            correct = shiftLegacyOneBasedAnswer(standard).equals(selected);
        }
        return new GradeResult(correct ? totalScore : 0, correct);
    }

    /**
     * 对需要完全匹配的答案进行判分
     *
     * @param standardAnswer 规范化标准答案
     * @param studentAnswer 规范化学生答案
     * @param totalScore 题目总分
     * @return 判分结果
     */
    private static GradeResult gradeExact(String standardAnswer, String studentAnswer, int totalScore) {
        boolean correct = !standardAnswer.isEmpty() && standardAnswer.equals(studentAnswer);
        return new GradeResult(correct ? totalScore : 0, correct);
    }

    /**
     * 对不定项选择题进行判分
     *
     * <p>选中错误选项得零分，只选部分正确选项时按比例向下取整，全部选对得满分。</p>
     *
     * @param standardAnswer 标准答案
     * @param studentAnswer 学生答案
     * @param totalScore 题目总分
     * @param legacyCompatible 是否兼容历史题库的 1-based 答案
     * @return 判分结果
     */
    private static GradeResult gradeUncertainty(String standardAnswer, String studentAnswer,
                                                int totalScore, boolean legacyCompatible) {
        Set<Integer> standard = parseChoiceAnswer(standardAnswer);
        Set<Integer> selected = parseChoiceAnswer(studentAnswer);
        if (standard.isEmpty() || selected.isEmpty()) {
            return new GradeResult(0, false);
        }

        Set<Integer> gradingStandard = standard;
        if (legacyCompatible && !standard.equals(selected) && !standard.contains(0)) {
            Set<Integer> legacyShifted = shiftLegacyOneBasedAnswer(standard);
            if (legacyShifted.containsAll(selected)) {
                gradingStandard = legacyShifted;
            }
        }
        if (!gradingStandard.containsAll(selected)) {
            return new GradeResult(0, false);
        }
        boolean correct = gradingStandard.equals(selected);
        int score = correct ? totalScore : totalScore * selected.size() / gradingStandard.size();
        return new GradeResult(score, correct);
    }

    /**
     * 解析逗号分隔的非负选项序号
     *
     * @param answer 原始答案
     * @return 选项序号集合；格式非法时返回空集合
     */
    private static Set<Integer> parseChoiceAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return Set.of();
        }
        String normalizedAnswer = answer.trim()
                .replace('，', ',')
                .replace('、', ',')
                .replace('；', ',')
                .replace(';', ',')
                .replace('|', ',');
        Set<Integer> indexes = new LinkedHashSet<>();
        for (String value : Arrays.stream(normalizedAnswer.split(",", -1)).map(String::trim).toList()) {
            if (value.isEmpty()) {
                return Set.of();
            }
            if (value.length() > 9
                    || value.chars().anyMatch(character -> character < '0' || character > '9')) {
                return Set.of();
            }
            int index;
            try {
                index = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                return Set.of();
            }
            if (index < 0 || !indexes.add(index)) {
                return Set.of();
            }
        }
        return indexes;
    }

    /**
     * 将旧题库中的 1-based 选择题答案转换为学生端使用的 0-based 下标。
     *
     * @param answer 原始答案下标
     * @return 转换后的答案下标
     */
    private static Set<Integer> shiftLegacyOneBasedAnswer(Set<Integer> answer) {
        Set<Integer> shifted = new LinkedHashSet<>();
        for (Integer index : answer) {
            if (index == null || index <= 0) {
                return Set.of();
            }
            shifted.add(index - 1);
        }
        return shifted;
    }

    /**
     * 将常见判断题答案统一转换为 0 或 1
     *
     * @param answer 原始答案
     * @param legacyCompatible 是否兼容历史题库“1 表示正确，其他数字表示错误”的约定
     * @return 0、1 或空字符串
     */
    private static String normalizeJudgeAnswer(String answer, boolean legacyCompatible) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String value = answer.trim().toLowerCase(Locale.ROOT);
        if (Set.of("1", "true", "正确", "对", "是").contains(value)) {
            return "1";
        }
        if (Set.of("0", "false", "错误", "错", "否").contains(value)) {
            return "0";
        }
        if (legacyCompatible && value.matches("\\d+")) {
            // 历史判断题约定：1 表示正确，其他数字均表示错误。
            return "0";
        }
        return "";
    }

    /**
     * 客观题判分结果
     *
     * @param score 实际得分
     * @param correct 是否完全正确
     */
    public record GradeResult(int score, boolean correct) {
    }
}
