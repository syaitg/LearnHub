package com.tianji.media.storage;

import com.tianji.media.domain.po.Media;
import com.tianji.media.enums.MediaSource;

import java.io.InputStream;
import java.util.List;

public interface IMediaStorage {

    /**
     * 获取临时上传授权签名
     * @return 签名信息
     */
    String getUploadSignature();

    /**
     * 获取临时上传授权签名
     * @param fieldId 视频文件id
     * @param userId 查看视频的用户的id，用于生成水印
     * @param freeExpire  免费试看时长，null则表示不限制时长
     * @return 签名信息
     */
    String getPlaySignature(String fieldId,Long userId, Integer freeExpire);

    /**
     * 根据媒资来源获取播放签名；未区分来源的旧调用默认使用官方账号。
     */
    default String getPlaySignature(String fieldId, Long userId, Integer freeExpire, MediaSource source) {
        return getPlaySignature(fieldId, userId, freeExpire);
    }

    /**
     * 根据媒资原始播放地址生成带 Key 防盗链参数的播放地址。
     *
     * <p>默认不生成地址；只有明确支持 URL 防盗链的存储实现才覆盖此方法。</p>
     *
     * @param mediaUrl 媒资原始播放地址
     * @param freeExpire 免费试看时长，单位分钟；null 表示不限制试看时长
     * @param source 媒资来源
     * @return 带 t、sign 参数的播放地址
     */
    default String getPlayUrl(String mediaUrl, Integer freeExpire, MediaSource source) {
        return null;
    }

    /**
     * 上传文件
     * @param filename 文件名称（a.mp4)
     * @param inputStream 文件流
     * @return requestId
     */
    MediaUploadResult uploadFile(String filename, InputStream inputStream, long contentLength);

    /**
     * 删除指定文件
     * @param fileId 文件唯一标识
     */
    void deleteFile(String fileId);

    /**
     * 按媒资来源删除指定文件；旧调用默认使用官方账号。
     */
    default void deleteFile(String fileId, MediaSource source) {
        deleteFile(fileId);
    }

    /**
     * 删除指定文件
     * @param fileIds 文件唯一标识的集合
     */
    void deleteFiles(List<String> fileIds);

    /**
     * 根据fileId查询文件信息
     * @param fileIds 多个文件标示
     * @return 文件信息列表
     */
    List<Media> queryMediaInfos(String ... fileIds);

    /**
     * 按媒资来源查询文件信息；旧调用默认使用官方账号。
     */
    default List<Media> queryMediaInfos(MediaSource source, String... fileIds) {
        return queryMediaInfos(fileIds);
    }
    /**
     * 获取媒资所属腾讯云账号的 APPID，用于播放器选择正确的账号。
     */
    default Long getVodAppId(MediaSource source) {
        return null;
    }

}
