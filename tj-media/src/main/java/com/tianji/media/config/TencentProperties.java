package com.tianji.media.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tj.tencent")
public class TencentProperties {
    private Long appId;
    private String secretId;
    private String secretKey;
    private VodProperties vod;
    /** 自有腾讯云 VOD 配置；新上传视频、播放签名和删除操作统一使用此账号。 */
    private VodProperties ownVod;
    /** 黑马官方 COS 配置，兼容原有 cos 节点。 */
    private CosProperties cos;
    /** 自有 COS 配置；新上传图片使用此账号。 */
    private CosProperties ownCos;
    @Data
    public static class VodProperties{
        /* 是否启用腾讯云 VOD */
        private boolean enable;
        /* 签名有效时间 */
        private long vodValidSeconds;
        /* 地域 */
        private String region;
        /* 任务流 */
        private String procedure;
        /* 播放密钥：官方 VOD 用于生成 psign，自有 VOD 用于生成 Key 防盗链的 t、sign 参数 */
        private String urlKey;
        /* 播放器配置 */
        private String pfcg;
        /** 自有账号的 APPID；官方配置继续使用根级 appId。 */
        private Long appId;
        /** 自有账号的 SecretId。 */
        private String secretId;
        /** 自有账号的 SecretKey。 */
        private String secretKey;
        /** VOD 事件轮询配置。 */
        private EventPullProperties eventPull;
    }
    @Data
    public static class EventPullProperties {
        /** 是否开启当前 VOD 账号的事件轮询。 */
        private boolean enabled;
        /** 事件轮询固定间隔，单位为毫秒。 */
        private long fixedDelay = 10000L;
    }
    @Data
    public static class CosProperties{
        /* 是否启用腾讯云 COS */
        private boolean enable;
        /* 地域 */
        private String region;
        /* 存储桶 */
        private String bucket;
        /* 触发分块上传的阈值 */
        private long multipartUploadThreshold;
        /* 分块上传的最小分块大小 */
        private long minimumUploadPartSize;
        /** 自有 COS 账号的 APPID；为空时兼容使用根级 appId。 */
        private Long appId;
        /** 自有 COS 账号的 SecretId；为空时兼容使用根级 SecretId。 */
        private String secretId;
        /** 自有 COS 账号的 SecretKey；为空时兼容使用根级 SecretKey。 */
        private String secretKey;
    }
}
