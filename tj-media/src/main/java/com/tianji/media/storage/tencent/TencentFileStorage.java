package com.tianji.media.storage.tencent;

import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.DeleteObjectsRequest.KeyVersion;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.Upload;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.AssertUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.media.config.TencentProperties;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.storage.IFileStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static com.tianji.media.enums.FileErrorInfo.Msg.*;

@Slf4j
public class TencentFileStorage implements IFileStorage {

    private final COSClient officialCosClient;
    private final COSClient ownCosClient;
    private final TransferManager officialTransferManager;
    private final TransferManager ownTransferManager;
    private final String officialBucketName;
    private final String ownBucketName;
    private final String officialRegion;
    private final String ownRegion;

    public TencentFileStorage(COSClient officialCosClient, COSClient ownCosClient,
                              TransferManager officialTransferManager, TransferManager ownTransferManager,
                              TencentProperties properties) {
        this.officialCosClient = officialCosClient;
        this.ownCosClient = ownCosClient;
        this.officialTransferManager = officialTransferManager;
        this.ownTransferManager = ownTransferManager;
        TencentProperties.CosProperties officialCos = requireCos(properties.getCos(), "tj.tencent.cos", false);
        TencentProperties.CosProperties ownCos = requireCos(properties.getOwnCos(), "tj.tencent.own-cos", true);
        Long officialAppId = requireAppId(properties.getAppId(), "黑马官方 COS 未配置 APPID：tj.tencent.appId");
        Long ownAppId = requireAppId(ownCos.getAppId(), "自有 COS 未配置 APPID：tj.tencent.own-cos.appId");
        this.officialBucketName = buildBucketName(officialCos, officialAppId);
        this.ownBucketName = buildBucketName(ownCos, ownAppId);
        this.officialRegion = officialCos.getRegion();
        this.ownRegion = ownCos.getRegion();
        // 记录当前实际使用的存储桶和地域，便于确认配置是否已经生效，但不记录敏感密钥
        log.info("当前文件存储：腾讯云 COS 双账号，officialBucket={}，officialRegion={}，ownBucket={}，ownRegion={}",
                this.officialBucketName, this.officialRegion, this.ownBucketName, this.ownRegion);
    }

    @Override
    public String uploadFile(String key, InputStream inputStream, long contentLength) {
        // 新上传文件默认使用自有腾讯云 COS
        return uploadFile(key, inputStream, contentLength, MediaSource.OWN_TENCENT);
    }

    @Override
    public String uploadFile(String key, InputStream inputStream, long contentLength, MediaSource source) {
        MediaSource actualSource = normalizeSourceForUpload(source);
        String bucketName = bucketName(actualSource);
        TransferManager transferManager = transferManager(actualSource);
        String region = region(actualSource);
        // 1.数据校验
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);
        AssertUtils.isNotNull(inputStream);

        // 2.元信息，主要是文件大小，这样才可以利用分片上传功能
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(contentLength);

        // 3.请求对象
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream, objectMetadata);

        try {
            // 4.异步发起上传，返回异步结果upload
            Upload upload = transferManager.upload(putObjectRequest);
            // 5.等待结果
            UploadResult result = upload.waitForUploadResult();
            // https://tianji-1259405500.cos.ap-nanjing.myqcloud.com/f00076a59de6410d8262ca48c1c64ec9.png
            return StrUtil.format("https://{}.cos.{}.myqcloud.com/{}",
                    bucketName,
                    region,
                    key);
        } catch (Exception e) {
            log.error("上传文件[{}]时发生异常：", key, e);
            // 将腾讯云返回的错误码带回前端，便于区分权限、桶名、地域和网络问题
            Throwable cause = e;
            while (cause.getCause() != null && cause != cause.getCause()) {
                if (cause instanceof CosServiceException) {
                    break;
                }
                cause = cause.getCause();
            }
            String reason = cause.getMessage();
            if (cause instanceof CosServiceException cosServiceException) {
                reason = StrUtil.format("COS错误码={}，HTTP状态={}",
                        cosServiceException.getErrorCode(), cosServiceException.getStatusCode());
            } else if (cause instanceof CosClientException) {
                reason = "COS客户端异常：" + reason;
            }
            if (StrUtil.isBlank(reason)) {
                reason = "请查看媒资服务日志";
            }
            throw new CommonException("文件上传异常：" + reason, e);
        }
    }

    @Override
    public InputStream downloadFile(String key) {
        // 未区分来源的旧调用默认读取黑马官方 COS
        return downloadFile(key, MediaSource.OFFICIAL_TENCENT);
    }

    @Override
    public InputStream downloadFile(String key, MediaSource source) {
        MediaSource actualSource = normalizeSource(source);
        String bucketName = bucketName(actualSource);
        COSClient cosClient = cosClient(actualSource);
        // 1.数据校验
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);
        // 2.准备请求参数
        GetObjectRequest request = new GetObjectRequest(bucketName, key);
        try {
            // 3.下载
            COSObject cosObject = cosClient.getObject(request);
            return cosObject.getObjectContent();
        } catch (Exception e) {
            log.error("下载文件[{}]时发生异常：", key, e);
            throw new CommonException("文件下载异常。", e);
        }
    }

    @Override
    public String getFileUrl(String key, MediaSource source) {
        MediaSource actualSource = normalizeSource(source);
        return StrUtil.format("https://{}.cos.{}.myqcloud.com/{}",
                bucketName(actualSource), region(actualSource), key);
    }

    @Override
    public void deleteFile(String key) {
        // 未区分来源的旧调用默认删除黑马官方 COS 文件
        deleteFile(key, MediaSource.OFFICIAL_TENCENT);
    }

    @Override
    public void deleteFile(String key, MediaSource source) {
        MediaSource actualSource = normalizeSource(source);
        String bucketName = bucketName(actualSource);
        COSClient cosClient = cosClient(actualSource);
        // 1.数据校验
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);
        try {
            // 2.删除
            cosClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            log.error("删除文件[{}]时发生异常：", key, e);
            throw new CommonException("删除异常。", e);
        }

    }

    @Override
    public void deleteFiles(List<String> keys) {
        // 未区分来源的旧调用默认删除黑马官方 COS 文件
        deleteFiles(keys, MediaSource.OFFICIAL_TENCENT);
    }

    @Override
    public void deleteFiles(List<String> keys, MediaSource source) {
        MediaSource actualSource = normalizeSource(source);
        String bucketName = bucketName(actualSource);
        COSClient cosClient = cosClient(actualSource);
        // 1.数据校验
        if (CollUtils.isEmpty(keys)) {
            return;
        }
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);
        if (keys.size() > 1000) {
            throw new BadRequestException(FILE_KEY_TOO_MANY);
        }
        // 2.准备request
        DeleteObjectsRequest request = new DeleteObjectsRequest(bucketName);
        // 3.设置要删除的key列表, 最多一次删除1000个
        List<KeyVersion> keyList = keys.stream().map(KeyVersion::new).collect(Collectors.toList());
        request.setKeys(keyList);
        try {
            // 4.删除
            cosClient.deleteObjects(request);
        } catch (Exception e) {
            log.error("批量删除文件[{}]时发生异常：", keys, e);
            throw new CommonException("删除异常。", e);
        }
    }

    /** 根据来源选择 COS 客户端。 */
    private COSClient cosClient(MediaSource source) {
        return source == MediaSource.OWN_TENCENT ? ownCosClient : officialCosClient;
    }

    /** 根据来源选择分片上传管理器。 */
    private TransferManager transferManager(MediaSource source) {
        return source == MediaSource.OWN_TENCENT ? ownTransferManager : officialTransferManager;
    }

    /** 根据来源选择存储桶。 */
    private String bucketName(MediaSource source) {
        return source == MediaSource.OWN_TENCENT ? ownBucketName : officialBucketName;
    }

    /** 根据来源选择地域。 */
    private String region(MediaSource source) {
        return source == MediaSource.OWN_TENCENT ? ownRegion : officialRegion;
    }

    /** 历史文件来源为空时按黑马官方 COS 处理。 */
    private MediaSource normalizeSource(MediaSource source) {
        return source == null ? MediaSource.OFFICIAL_TENCENT : source;
    }

    /** 新上传文件未指定来源时默认使用自有 COS。 */
    private MediaSource normalizeSourceForUpload(MediaSource source) {
        return source == null ? MediaSource.OWN_TENCENT : source;
    }

    private TencentProperties.CosProperties requireCos(TencentProperties.CosProperties cos,
                                                       String configPrefix,
                                                       boolean ownAccount) {
        if (cos == null) {
            throw new IllegalStateException("未配置" + (ownAccount ? "自有" : "黑马官方")
                    + "腾讯云 COS 参数：" + configPrefix);
        }
        if (!cos.isEnable()) {
            throw new IllegalStateException((ownAccount ? "自有" : "黑马官方")
                    + "腾讯云 COS 未启用，请将 " + configPrefix + ".enable 配置为 true");
        }
        if (cos.getBucket() == null || cos.getBucket().isBlank()) {
            throw new IllegalStateException(configPrefix + ".bucket 未配置");
        }
        if (cos.getRegion() == null || cos.getRegion().isBlank()) {
            throw new IllegalStateException(configPrefix + ".region 未配置");
        }
        if (cos.getMultipartUploadThreshold() <= 0 || cos.getMinimumUploadPartSize() <= 0) {
            throw new IllegalStateException(configPrefix + " 分片上传参数必须大于 0");
        }
        if (ownAccount && (cos.getAppId() == null || cos.getAppId() <= 0)) {
            throw new IllegalStateException(configPrefix + ".appId 未配置或无效");
        }
        return cos;
    }

    private Long requireAppId(Long appId, String message) {
        if (appId == null || appId <= 0) {
            throw new IllegalStateException(message);
        }
        return appId;
    }

    /** 兼容配置中填写完整桶名和只填写基础桶名两种写法。 */
    private String buildBucketName(TencentProperties.CosProperties cos, Long appId) {
        String suffix = "-" + appId;
        return cos.getBucket().endsWith(suffix) ? cos.getBucket() : cos.getBucket() + suffix;
    }

}
