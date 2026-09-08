package com.tianji.exam.enums;

/**
 * AI 出题范围类型枚举
 */
public enum AiQuestionScopeType {
    SECTION,
    CHAPTER;

    /**
     * 根据字符串解析出题范围类型
     *
     * @param value 类型字符串
     * @return 匹配的范围类型，无法匹配时返回 null
     */
    public static AiQuestionScopeType of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AiQuestionScopeType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}