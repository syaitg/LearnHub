package com.tianji.media.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 媒资所属的腾讯云账号来源。
 */
@Getter
public enum MediaSource {
    /** 黑马官方腾讯云VOD账号。 */
    OFFICIAL_TENCENT(1, "黑马官方腾讯云"),
    /** 当前系统自有的腾讯云VOD账号。 */
    OWN_TENCENT(2, "自有腾讯云");

    @EnumValue
    private final int value;
    private final String desc;

    MediaSource(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
