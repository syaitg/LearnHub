package com.tianji.exam.utils;

import cn.hutool.crypto.digest.DigestUtil;
import com.tianji.api.dto.aigc.AiGeneratedQuestionDTO;
import com.tianji.common.utils.CollUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 题目内容校验工具
 */
public final class AiQuestionContentValidator {

    private static final int MAX_OPTION_COUNT = 10;
    private static final int MAX_OPTION_LENGTH = 500;
    private static final int MAX_KNOWLEDGE_POINT_COUNT = 30;
    private static final int MAX_KNOWLEDGE_POINT_LENGTH = 100;
    private static final int MAX_SUBJECTIVE_ANSWER_LENGTH = 10000;

    /**
     * 工具类不允许实例化
     */
    private AiQuestionContentValidator() {
    }

    /**
     * 校验并规范化 AI 生成的题目内容
     *
     * @param question AI 生成题目
     * @return 题目校验结果
     */
    public static ValidationResult validate(AiGeneratedQuestionDTO question) {
        if (question == null) {
            return ValidationResult.invalid("题目为空");
        }
        if (question.getName() == null || question.getName().isBlank()) {
            return ValidationResult.invalid("题干不能为空");
        }
        if (question.getName().length() > 1000) {
            return ValidationResult.invalid("题干长度不能超过 1000 个字符");
        }
        Integer type = question.getType();
        if (type == null || type < 1 || type > 5) {
            return ValidationResult.invalid("题型不支持");
        }
        if (question.getDifficulty() == null || question.getDifficulty() < 1 || question.getDifficulty() > 3) {
            return ValidationResult.invalid("难度不合法");
        }
        if (question.getScore() == null || question.getScore() <= 0 || question.getScore() > 100) {
            return ValidationResult.invalid("分值不合法");
        }
        if (question.getAnswer() == null || question.getAnswer().isBlank()) {
            return ValidationResult.invalid("答案不能为空");
        }
        if (question.getAnalysis() == null || question.getAnalysis().isBlank()) {
            return ValidationResult.invalid("解析不能为空");
        }
        if (question.getAnalysis().trim().length() > 300) {
            return ValidationResult.invalid("解析长度不能超过 300 个字符");
        }

        if (type >= 1 && type <= 3) {
            ValidationResult optionResult = validateChoiceOptions(question.getOptions());
            if (!optionResult.valid()) {
                return optionResult;
            }
            List<String> options = normalizeChoiceOptions(question.getOptions());
            ValidationResult answerResult = validateChoiceAnswer(question.getAnswer(), options.size(), type == 1);
            if (!answerResult.valid()) {
                return answerResult;
            }
            question.setOptions(options);
            question.setAnswer(normalizeChoiceAnswer(question.getAnswer()));
        } else if (type == 4) {
            String answer = question.getAnswer().trim();
            if (!"0".equals(answer) && !"1".equals(answer)) {
                return ValidationResult.invalid("判断题答案只能是 0 或 1");
            }
            question.setOptions(List.of());
            question.setAnswer(answer);
        } else {
            String subjectiveAnswer = question.getAnswer().trim();
            if (subjectiveAnswer.length() > MAX_SUBJECTIVE_ANSWER_LENGTH) {
                return ValidationResult.invalid("主观题参考答案长度不能超过 10000 个字符");
            }
            question.setOptions(List.of());
            question.setAnswer(subjectiveAnswer);
        }

        question.setName(question.getName().trim());
        question.setAnalysis(question.getAnalysis().trim());
        ValidationResult knowledgePointResult = validateKnowledgePoints(question);
        if (!knowledgePointResult.valid()) {
            return knowledgePointResult;
        }
        return ValidationResult.success();
    }

    /**
     * 计算规范化后的题目内容指纹
     *
     * @param name 题干
     * @param options 题目选项
     * @return SHA-256 内容指纹
     */
    public static String fingerprint(String name, List<String> options) {
        return fingerprint(null, name, options);
    }

    /**
     * 计算包含题型的规范化题目内容指纹
     *
     * @param type 题型
     * @param name 题干
     * @param options 题目选项
     * @return SHA-256 内容指纹
     */
    public static String fingerprint(Integer type, String name, List<String> options) {
        String normalizedType = type == null ? "" : type.toString();
        String normalizedName = normalizeText(name);
        String normalizedOptions = cleanOptions(options).stream()
                .map(AiQuestionContentValidator::normalizeText)
                .collect(Collectors.joining("|"));
        return DigestUtil.sha256Hex(normalizedType + "#" + normalizedName + "#" + normalizedOptions);
    }

    /**
     * 校验选择题选项，避免空选项被删除后造成答案下标错位
     *
     * @param options 原始选项
     * @return 选项校验结果
     */
    private static ValidationResult validateChoiceOptions(List<String> options) {
        if (CollUtils.isEmpty(options) || options.size() < 2) {
            return ValidationResult.invalid("选择题至少需要两个选项");
        }
        if (options.size() > MAX_OPTION_COUNT) {
            return ValidationResult.invalid("选择题选项数量不能超过 10 个");
        }
        Set<String> normalizedOptions = new LinkedHashSet<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                return ValidationResult.invalid("选择题选项不能为空");
            }
            String normalized = option.trim();
            if (normalized.length() > MAX_OPTION_LENGTH) {
                return ValidationResult.invalid("单个选择题选项长度不能超过 500 个字符");
            }
            if (!normalizedOptions.add(normalizeComparableText(normalized))) {
                return ValidationResult.invalid("选择题不能包含重复选项");
            }
        }
        return ValidationResult.success();
    }

    /**
     * 清理已通过校验的选择题选项
     *
     * @param options 原始选项
     * @return 去除首尾空格后的选项
     */
    private static List<String> normalizeChoiceOptions(List<String> options) {
        return options.stream().map(String::trim).toList();
    }

    /**
     * 校验并规范化题目知识点
     *
     * @param question AI 生成题目
     * @return 知识点校验结果
     */
    private static ValidationResult validateKnowledgePoints(AiGeneratedQuestionDTO question) {
        List<String> points = question.getKnowledgePoints();
        if (CollUtils.isEmpty(points)) {
            question.setKnowledgePoints(List.of());
            return ValidationResult.success();
        }
        if (points.size() > MAX_KNOWLEDGE_POINT_COUNT) {
            return ValidationResult.invalid("题目知识点数量不能超过 30 个");
        }
        Map<String, String> normalizedPoints = new LinkedHashMap<>();
        for (String point : points) {
            if (point == null || point.isBlank()) {
                return ValidationResult.invalid("题目知识点不能为空");
            }
            String normalized = point.trim();
            if (normalized.length() > MAX_KNOWLEDGE_POINT_LENGTH) {
                return ValidationResult.invalid("单个知识点长度不能超过 100 个字符");
            }
            normalizedPoints.putIfAbsent(normalizeComparableText(normalized), normalized);
        }
        question.setKnowledgePoints(new ArrayList<>(normalizedPoints.values()));
        return ValidationResult.success();
    }

    /**
     * 校验选择题答案下标
     *
     * @param answer 答案文本
     * @param optionCount 选项数量
     * @param single 是否为单选题
     * @return 答案校验结果
     */
    private static ValidationResult validateChoiceAnswer(String answer, int optionCount, boolean single) {
        List<String> answerIndexes = splitAnswerIndexes(answer);
        if (answerIndexes.isEmpty()) {
            return ValidationResult.invalid("选择题答案不能为空");
        }
        if (answerIndexes.stream().anyMatch(AiQuestionContentValidator::isInvalidIndex)) {
            return ValidationResult.invalid("选择题答案包含非法下标");
        }

        List<Integer> indexes = answerIndexes.stream()
                .map(Integer::valueOf)
                .toList();
        Set<Integer> unique = new LinkedHashSet<>(indexes);
        if (unique.size() != indexes.size()) {
            return ValidationResult.invalid("选择题答案包含重复下标");
        }
        if (single && indexes.size() != 1) {
            return ValidationResult.invalid("单选题只能有一个答案");
        }
        if (indexes.stream().anyMatch(index -> index < 0 || index >= optionCount)) {
            return ValidationResult.invalid("选择题答案下标超出选项范围");
        }
        return ValidationResult.success();
    }

    /**
     * 规范化选择题答案顺序
     *
     * @param answer 原始答案
     * @return 规范化后的答案
     */
    private static String normalizeChoiceAnswer(String answer) {
        return splitAnswerIndexes(answer).stream()
                .map(Integer::valueOf)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /**
     * 拆分并清理选择题答案下标，保留空项供格式校验
     *
     * @param answer 原始答案
     * @return 清理后的答案下标文本
     */
    private static List<String> splitAnswerIndexes(String answer) {
        return Arrays.stream(answer.split(",", -1))
                .map(String::trim)
                .toList();
    }

    /**
     * 判断答案下标是否不符合非负整数格式
     *
     * @param value 下标文本
     * @return true 表示格式不合法
     */
    private static boolean isInvalidIndex(String value) {
        return value.isEmpty() || value.length() > 9
                || value.chars().anyMatch(character -> character < '0' || character > '9');
    }

    /**
     * 清理用于指纹计算的题目选项
     *
     * @param options 原始选项
     * @return 清理后的选项
     */
    private static List<String> cleanOptions(List<String> options) {
        if (CollUtils.isEmpty(options)) {
            return new ArrayList<>();
        }
        return options.stream()
                .filter(option -> option != null && !option.isBlank())
                .map(String::trim)
                .toList();
    }

    /**
     * 规范化用于内容去重的文本
     *
     * @param text 原始文本
     * @return 规范化文本
     */
    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    /**
     * 规范化用于选项和知识点比较的文本，同时保留有意义的标点与符号
     *
     * @param text 原始文本
     * @return 规范化比较文本
     */
    private static String normalizeComparableText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    /**
     * AI 题目内容校验结果
     *
     * @param valid 是否校验通过
     * @param message 校验失败原因
     */
    public record ValidationResult(boolean valid, String message) {

        /**
         * 创建校验成功结果
         *
         * @return 校验成功结果
         */
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        /**
         * 创建校验失败结果
         *
         * @param message 校验失败原因
         * @return 校验失败结果
         */
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}