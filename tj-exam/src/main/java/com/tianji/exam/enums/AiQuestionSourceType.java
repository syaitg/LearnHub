package com.tianji.exam.enums;

/**
 * AI 出题来源类型枚举
 */
public enum AiQuestionSourceType {
    MANUAL_TOPIC,
    MATERIAL_TEXT,
    VIDEO_TRANSCRIPT,
    VIDEO_SUMMARY;

    /**
     * 根据字符串解析出题来源类型
     *
     * @param value 类型字符串
     * @return 匹配的来源类型，无法匹配时返回 null
     */
    public static AiQuestionSourceType of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AiQuestionSourceType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}