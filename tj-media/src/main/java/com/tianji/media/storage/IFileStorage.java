package com.tianji.media.storage;

import com.tianji.media.enums.MediaSource;

import java.io.InputStream;
import java.util.List;

public interface IFileStorage {

    /**
     * 上传文件
     * @param key 文件唯一标识（a.jpg)
     * @param inputStream 文件流
     * @return requestId
     */
    String uploadFile(String key, InputStream inputStream, long contentLength);

    /**
     * 按媒资来源上传文件；未区分来源的旧调用默认使用自有腾讯云。
     */
    default String uploadFile(String key, InputStream inputStream, long contentLength, MediaSource source) {
        return uploadFile(key, inputStream, contentLength);
    }

    /**
     * 下载文件
     * @param key 文件唯一标识（a.jpg)
     * @return 文件流
     */
    InputStream downloadFile(String key);

    /**
     * 按媒资来源下载文件；未区分来源的旧调用默认使用黑马官方腾讯云。
     */
    default InputStream downloadFile(String key, MediaSource source) {
        return downloadFile(key);
    }

    /**
     * 获取指定来源文件的访问地址。
     */
    default String getFileUrl(String key, MediaSource source) {
        return null;
    }

    /**
     * 删除指定文件
     * @param key 文件唯一标识（a.jpg)
     */
    void deleteFile(String key);

    /**
     * 按媒资来源删除文件；未区分来源的旧调用默认使用黑马官方腾讯云。
     */
    default void deleteFile(String key, MediaSource source) {
        deleteFile(key);
    }

    /**
     * 删除指定文件
     * @param keys 文件唯一标识（a.jpg)的集合
     */
    void deleteFiles(List<String> keys);

    /**
     * 按媒资来源批量删除文件；未区分来源的旧调用默认使用黑马官方腾讯云。
     */
    default void deleteFiles(List<String> keys, MediaSource source) {
        deleteFiles(keys);
    }
}
