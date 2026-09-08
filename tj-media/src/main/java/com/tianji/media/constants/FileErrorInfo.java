package com.tianji.media.constants;

public interface FileErrorInfo {
    String MEDIA_NOT_EXISTS = "媒资不存在";
    String MEDIA_NOT_FREE = "课程不支持试看";
    String USER_NOT_EXISTS = "用户信息不存在";
    String MEDIA_QUOTE_NOT_EXISTS = "媒资引用信息查询异常";
    String MEDIA_ID_REQUIRED = "媒资ID不能为空";
    String MEDIA_IN_USE = "媒资已被课程引用，无法删除";
    String MEDIA_DELETE_FAILED = "媒资删除失败";
    String MEDIA_SOURCE_NOT_CONFIRMED = "无法确认媒资所属腾讯云账号，已拒绝删除";
    String MEDIA_SAVE_FAILED = "media save failed";
    String SECTION_NOT_EXISTS = "课程小节不存在";
    String MEDIA_NOT_READY = "视频仍在处理中，请稍后再试";
}
