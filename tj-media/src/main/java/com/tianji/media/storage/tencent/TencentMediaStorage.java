package com.tianji.media.storage.tencent;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.Upload;
import com.qcloud.vod.common.FileUtil;
import com.qcloud.vod.common.StringUtil;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.*;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.StringUtils;
import com.tianji.media.config.TencentProperties;
import com.tianji.media.domain.po.Media;
import com.tianji.media.enums.FileStatus;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.storage.IMediaStorage;
import com.tianji.media.storage.MediaUploadResult;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.tianji.media.enums.FileErrorInfo.Msg.*;

@Slf4j
public class TencentMediaStorage implements IMediaStorage {
    private final VodClient officialVodClient;
    private final VodClient ownVodClient;
    private final TencentProperties tencentProperties;

    public TencentMediaStorage(VodClient officialVodClient, VodClient ownVodClient,
                               TencentProperties tencentProperties) {
        this.officialVodClient = officialVodClient;
        this.ownVodClient = ownVodClient;
        this.tencentProperties = tencentProperties;
    }

    private static final String CONTEXT_TEMPLATE =
            "secretId=%s&currentTimeStamp=%d&expireTime=%d&random=%d";
    private static final String CONTEXT_TEMPLATE_WITH_PROCEDURE =
            "secretId=%s&currentTimeStamp=%d&expireTime=%d&random=%d&procedure=%s";
    private static final String[] MEDIA_INFO_FILTERS = new String[]{"basicInfo", "metaData"};
    @Override
    public String getUploadSignature() {
        // 新上传视频统一使用自有 VOD 账号，避免继续占用黑马官方账号的上传权限。
        TencentProperties.VodProperties vod = ownVodProperties();
        String secretId = vodSecretId(vod);
        String secretKey = vodSecretKey(vod);
        // 计算签名上下文。
        HMac mac = new HMac(HmacAlgorithm.HmacSHA1, secretKey.getBytes(StandardCharsets.UTF_8));
        // 计算签名有效期。
        long now = System.currentTimeMillis() / 1000;
        long endTime;
        try {
            endTime = Math.addExact(now, vod.getVodValidSeconds());
        } catch (ArithmeticException e) {
            throw new CommonException("VOD 有效期配置超出可计算范围：vodValidSeconds", e);
        }
        String procedure = vod.getProcedure();
        String context;
        if (StringUtils.isBlank(procedure)) {
            context = String.format(CONTEXT_TEMPLATE,
                    URLEncoder.encode(secretId, StandardCharsets.UTF_8),
                    now,
                    endTime,
                    RandomUtil.randomInt(0, Integer.MAX_VALUE)
            );
        }else{
            context = String.format(CONTEXT_TEMPLATE_WITH_PROCEDURE,
                    URLEncoder.encode(secretId, StandardCharsets.UTF_8),
                    now,
                    endTime,
                    RandomUtil.randomInt(0, Integer.MAX_VALUE),
                    procedure
            );
        }

        // 组合签名内容并进行 Base64 编码。
        byte[] bytes = ArrayUtil.addAll(mac.digest(context), context.getBytes(StandardCharsets.UTF_8));
        return Base64.encode(bytes);
    }

    @Override
    public String getPlaySignature(String fieldId, Long userId, Integer freeExpired) {
        return getPlaySignature(fieldId, userId, freeExpired, MediaSource.OFFICIAL_TENCENT);
    }

    @Override
    public String getPlaySignature(String fieldId, Long userId, Integer freeExpired, MediaSource source) {
        MediaSource actualSource = source == null ? MediaSource.OFFICIAL_TENCENT : source;
        // 自有视频使用原始播放地址和 urlKey 生成 t、sign，不生成会触发 1009 错误的 psign。
        if (actualSource == MediaSource.OWN_TENCENT) {
            throw new CommonException("自有腾讯云媒资统一使用 Key 防盗链播放地址，不能生成 psign");
        }
        TencentProperties.VodProperties vod = vodProperties(actualSource);
        String configPrefix = vodConfigPrefix(actualSource);
        if (StringUtils.isBlank(fieldId)) {
            throw new CommonException("视频文件 ID 不能为空");
        }
        String urlKey = vod.getUrlKey();
        if (StringUtils.isBlank(urlKey)) {
            throw new CommonException(String.format(
                    "%s未配置播放密钥：请在 Nacos 的 media-service.yaml 中配置 %s.urlKey",
                    actualSource.getDesc(), configPrefix));
        }
        if (!urlKey.equals(urlKey.trim())) {
            throw new CommonException(String.format(
                    "%s防盗链 Key 首尾存在空格或换行，请检查 Nacos 配置 %s.urlKey",
                    actualSource.getDesc(), configPrefix));
        }
        Long appId = vodAppId(actualSource);
        if (appId == null || appId <= 0) {
            throw new CommonException(String.format(
                    "%s未配置 APPID：请在 Nacos 的 media-service.yaml 中配置 %s.appId",
                    actualSource.getDesc(), configPrefix));
        }
        long currentTime = System.currentTimeMillis() / 1000;
        validateFreeExpire(freeExpired);

        HashMap<String, Object> urlAccessInfo = new HashMap<>(2);
       /* if (userId != null) {
            urlAccessInfo.put("uid", String.valueOf(userId));
        }*/
        if (freeExpired != null) {
            urlAccessInfo.put("exper", previewSeconds(freeExpired));
        }
        // 官方媒资继续使用原有播放器签名结构。
        JWT jwt = JWT.create()
                .setSigner(JWTSignerUtil.hs256(urlKey.getBytes(StandardCharsets.UTF_8)))
                .setPayload("appId", appId)
                .setPayload("fileId", fieldId)
                .setPayload("currentTimeStamp", currentTime)
                .setPayload("urlAccessInfo", urlAccessInfo);
        // 官方媒资继续使用原有播放器配置，避免破坏历史转码或 DRM 视频播放。
        if (StringUtils.isNotBlank(vod.getPfcg())) {
            jwt.setPayload("pcfg", vod.getPfcg());
        }
        String signature = jwt.sign();
        // 腾讯云校验失败发生在播放器侧，媒资服务收不到异常，因此日志中不记录明文密钥。
        log.debug("已生成腾讯云 VOD 播放签名：source={}，appId={}，fileId={}，algorithm=HS256，urlKeyLength={}，urlKeyFingerprint={}",
                actualSource, appId, fieldId, urlKey.length(), urlKeyFingerprint(urlKey));
        return signature;
    }

    @Override
    public String getPlayUrl(String mediaUrl, Integer freeExpire, MediaSource source) {
        MediaSource actualSource = source == null ? MediaSource.OFFICIAL_TENCENT : source;
        TencentProperties.VodProperties vod = vodProperties(actualSource);
        String configPrefix = vodConfigPrefix(actualSource);
        if (StringUtils.isBlank(mediaUrl)) {
            throw new CommonException("视频原始播放地址不能为空，无法生成防盗链播放地址");
        }
        String urlKey = vod.getUrlKey();
        if (StringUtils.isBlank(urlKey)) {
            throw new CommonException(String.format(
                    "%s未配置防盗链 Key，请在 Nacos 的 media-service.yaml 中配置 %s.urlKey",
                    actualSource.getDesc(), configPrefix));
        }
        if (!urlKey.equals(urlKey.trim())) {
            throw new CommonException(String.format(
                    "%s防盗链 Key 首尾存在空格或换行，请检查 Nacos 配置 %s.urlKey",
                    actualSource.getDesc(), configPrefix));
        }
        if (!urlKey.matches("[A-Za-z0-9]{8,20}")) {
            throw new CommonException(String.format(
                    "%s防盗链 Key 必须是 8-20 位字母或数字，请检查 Nacos 配置 %s.urlKey",
                    actualSource.getDesc(), configPrefix));
        }
        if (vod.getVodValidSeconds() <= 0) {
            throw new CommonException(String.format(
                    "%s未配置有效的防盗链过期时间，请检查 Nacos 配置 %s.vodValidSeconds",
                    actualSource.getDesc(), configPrefix));
        }
        try {
            URI origin = URI.create(mediaUrl.trim());
            String rawPath = origin.getRawPath();
            int lastSlash = rawPath == null ? -1 : rawPath.lastIndexOf('/');
            if (!("http".equalsIgnoreCase(origin.getScheme())
                    || "https".equalsIgnoreCase(origin.getScheme()))
                    || StringUtils.isBlank(origin.getRawAuthority())
                    || StringUtils.isBlank(rawPath)
                    || lastSlash < 0 || lastSlash == rawPath.length() - 1) {
                throw new CommonException("视频原始播放地址格式错误，无法生成防盗链播放地址");
            }
            if (StringUtils.isNotBlank(origin.getRawQuery())) {
                throw new CommonException("视频原始播放地址已包含查询参数，无法生成统一的防盗链播放地址");
            }
            // 腾讯云要求 t 使用十六进制 Unix 时间戳，t 是必填参数。
            long expireTimestamp;
            try {
                expireTimestamp = Math.addExact(System.currentTimeMillis() / 1000,
                        vod.getVodValidSeconds());
            } catch (ArithmeticException e) {
                throw new CommonException(String.format(
                        "%s防盗链过期时间超出可计算范围：请检查 Nacos 配置 %s.vodValidSeconds",
                        actualSource.getDesc(), configPrefix), e);
            }
            String t = Long.toHexString(expireTimestamp);
            String directory = rawPath.substring(0, lastSlash + 1);
            validateFreeExpire(freeExpire);
            String exper = freeExpire == null ? null : String.valueOf(previewSeconds(freeExpire));
            // 按腾讯云 VOD 规则计算 sign：MD5(Key + Dir + t + exper)。
            // 不使用可选的 us、rlimit 参数，exper 仅在免费试看时长存在时加入。
            String signSource = urlKey + directory + t + (exper == null ? "" : exper);
            String sign = DigestUtil.md5Hex(signSource);
            String baseUrl = origin.getScheme() + "://" + origin.getRawAuthority() + rawPath;
            String playUrl = baseUrl + "?t=" + t;
            if (exper != null) {
                playUrl += "&exper=" + exper;
            }
            playUrl += "&sign=" + sign;
            log.debug("已生成腾讯云 VOD 防盗链播放地址：source={}，host={}，path={}，urlKeyLength={}，urlKeyFingerprint={}",
                    actualSource, origin.getHost(), rawPath, urlKey.length(), urlKeyFingerprint(urlKey));
            return playUrl;
        } catch (IllegalArgumentException e) {
            throw new CommonException("视频原始播放地址格式错误，无法生成防盗链播放地址", e);
        }
    }

    @Override
    public MediaUploadResult uploadFile(String filename, InputStream inputStream, long contentLength) {
        CommitUploadResponse response = null;
        try {
            // 1.申请上传。
            ApplyUploadResponse applyUploadResponse = applyUpload(filename, ownVodClient, ownVodProperties());
            // 2.开始上传。
            handleUpload(inputStream, contentLength, applyUploadResponse);
            // 3.确认上传。
            response = commitUpload(applyUploadResponse, ownVodClient);
        } catch (Exception e) {
            log.error("上传视频文件[{}]时发生异常", filename, e);
            throw new CommonException(MEDIA_UPLOAD_ERROR, e);
        }
        // 4.解析上传结果。
        // 5.检查上传结果。
        if (response == null || StringUtils.isBlank(response.getFileId())) {
            throw new CommonException("腾讯云 VOD 上传成功但未返回有效文件编号，请稍后在媒资管理中重试");
        }
        MediaUploadResult result = new MediaUploadResult();
        result.setFileId(response.getFileId());
        result.setMediaUrl(response.getMediaUrl());
        result.setCoverUrl(response.getCoverUrl());
        result.setRequestId(response.getRequestId());
        result.setSource(MediaSource.OWN_TENCENT);
        return result;
    }

    private CommitUploadResponse commitUpload(ApplyUploadResponse applyUploadResponse, VodClient client) {
        CommitUploadRequest request = new CommitUploadRequest();
        request.setVodSessionKey(applyUploadResponse.getVodSessionKey());
        TencentCloudSDKException err = null;
        int i = 0;
        while (i < 4) {
            try {
                return client.CommitUpload(request);
            } catch (TencentCloudSDKException e) {
                if (StringUtil.isEmpty(e.getRequestId())) {
                    err = e;
                    ++i;
                    continue;
                }

                throw new CommonException(MEDIA_COMMIT_UPLOAD_ERROR, e);
            }

        }
        throw new CommonException(MEDIA_COMMIT_UPLOAD_ERROR, err);
    }

    private void handleUpload(InputStream inputStream, long contentLength, ApplyUploadResponse applyUploadResponse) {
        // 1.整理授权上传凭证。
        COSCredentials credentials = null;
        if (applyUploadResponse.getTempCertificate() != null) {
            TempCertificate certificate = applyUploadResponse.getTempCertificate();
            credentials = new BasicSessionCredentials(certificate.getSecretId(), certificate.getSecretKey(), certificate.getToken());
        } else {
            credentials = new BasicCOSCredentials(
                    vodSecretId(ownVodProperties()), vodSecretKey(ownVodProperties()));
        }
        // 2.准备上传客户端。
        ClientConfig clientConfig = new ClientConfig(new Region(applyUploadResponse.getStorageRegion()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        COSClient cosClient = new COSClient(credentials, clientConfig);
        TransferManager transferManager = new TransferManager(cosClient);
        try {
            // 3.开始上传。
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            Upload upload = transferManager.upload(applyUploadResponse.getStorageBucket(), applyUploadResponse.getMediaStoragePath(), inputStream, metadata);
            // 4.等待上传完成。
            upload.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 5.关闭上传客户端。
            transferManager.shutdownNow();
        }
    }

    private ApplyUploadResponse applyUpload(String filename, VodClient client, TencentProperties.VodProperties vod) {
        ApplyUploadRequest req = new ApplyUploadRequest();
        req.setMediaName(filename);
        req.setMediaType(FileUtil.getFileType(filename));
        if (StringUtils.isNotBlank(vod.getProcedure())) {
            req.setProcedure(vod.getProcedure());
        }
        TencentCloudSDKException err = null;
        int i = 0;
        while (i < 4) {
            try {
                // 返回的上传响应实例与请求对象对应。
                return client.ApplyUpload(req);
            } catch (TencentCloudSDKException e) {
                if (StringUtil.isEmpty(e.getRequestId())) {
                    err = e;
                    ++i;
                    continue;
                }
                throw new CommonException(MEDIA_APPLY_UPLOAD_ERROR, e);
            }
        }
        throw new RuntimeException(MEDIA_APPLY_UPLOAD_ERROR, err);
    }

    @Override
    public void deleteFile(String fileId) {
        deleteFile(fileId, MediaSource.OFFICIAL_TENCENT);
    }

    @Override
    public void deleteFile(String fileId, MediaSource source) {
        try {
            DeleteMediaRequest request = new DeleteMediaRequest();
            request.setFileId(fileId);
            vodClient(source).DeleteMedia(request);
        } catch (TencentCloudSDKException e) {
            throw new CommonException(MEDIA_DELETE_ERROR, e);
        }
    }

    @Override
    public void deleteFiles(List<String> fileIds) {
        for (String fileId : fileIds) {
            deleteFile(fileId);
        }
    }

    @Override
    public List<Media> queryMediaInfos(String ... fileIds) {
        return queryMediaInfos(MediaSource.OFFICIAL_TENCENT, fileIds);
    }

    @Override
    public List<Media> queryMediaInfos(MediaSource source, String ... fileIds) {
        MediaSource actualSource = source == null ? MediaSource.OFFICIAL_TENCENT : source;
        if (fileIds == null || fileIds.length == 0) {
            return Collections.emptyList();
        }
        // 1.准备请求参数。
        DescribeMediaInfosRequest req = new DescribeMediaInfosRequest();
        req.setFileIds(fileIds);
        req.setFilters(MEDIA_INFO_FILTERS);
        // 2.发送请求。
        DescribeMediaInfosResponse resp = null;
        try {
            resp = vodClient(actualSource).DescribeMediaInfos(req);
        } catch (TencentCloudSDKException e) {
            throw new RuntimeException("获取媒资信息异常", e);
        }

        // 3.解析结果。
        MediaInfo[] mediaInfoSet = resp.getMediaInfoSet();
        if (mediaInfoSet == null || mediaInfoSet.length == 0) {
            return Collections.emptyList();
        }
        // 4.转换数据。
        List<Media> list = new ArrayList<>(mediaInfoSet.length);
        for (MediaInfo info : mediaInfoSet) {
            Media media = new Media();
            media.setFileId(info.getFileId());
            media.setSource(actualSource);
            MediaMetaData md = info.getMetaData();
            MediaBasicInfo bi = info.getBasicInfo();
            if (bi != null) {
                media.setMediaUrl(bi.getMediaUrl());
                media.setFilename(bi.getName());
                media.setCoverUrl(bi.getCoverUrl());
            }
            if (md != null) {
                media.setSize(md.getSize());
                media.setDuration(md.getDuration());
            }
            media.setStatus(FileStatus.UPLOADED);
            list.add(media);
        }
        return list;
    }
    /** 根据来源选择 VOD 账号；历史数据来源为空时按官方账号处理。 */
    private VodClient vodClient(MediaSource source) {
        VodClient client = source == MediaSource.OWN_TENCENT ? ownVodClient : officialVodClient;
        if (client == null) {
            throw new CommonException(String.format(
                    "%s VOD 客户端未配置", source.getDesc()));
        }
        return client;
    }

    /** 根据来源选择 VOD 配置。 */
    private TencentProperties.VodProperties vodProperties(MediaSource source) {
        return source == MediaSource.OWN_TENCENT ? ownVodProperties() : tencentProperties.getVod();
    }

    /** 获取自有 VOD 配置；自有账号必须独立配置，禁止回退到官方账号。 */
    private void validateFreeExpire(Integer freeExpire) {
        if (freeExpire != null && freeExpire <= 0) {
            throw new CommonException("免费试看时长必须大于 0 分钟，无法生成防盗链播放地址");
        }
    }

    private long previewSeconds(Integer freeExpire) {
        validateFreeExpire(freeExpire);
        try {
            return Math.multiplyExact(freeExpire.longValue(), 60L);
        } catch (ArithmeticException e) {
            throw new CommonException("预览时长超出可计算范围", e);
        }
    }

    private TencentProperties.VodProperties ownVodProperties() {
        TencentProperties.VodProperties own = tencentProperties.getOwnVod();
        if (own == null) {
            throw new CommonException("未配置自有腾讯云 VOD，请在 Nacos 的 media-service.yaml 中配置 tj.tencent.own-vod");
        }
        return own;
    }

    private String vodSecretId(TencentProperties.VodProperties vod) {
        if (StringUtils.isBlank(vod.getSecretId())) {
            throw new CommonException("自有腾讯云 VOD 未配置 SecretId，请配置 tj.tencent.own-vod.secretId");
        }
        return vod.getSecretId();
    }

    private String vodSecretKey(TencentProperties.VodProperties vod) {
        if (StringUtils.isBlank(vod.getSecretKey())) {
            throw new CommonException("自有腾讯云 VOD 未配置 SecretKey，请配置 tj.tencent.own-vod.secretKey");
        }
        return vod.getSecretKey();
    }

    private String vodConfigPrefix(MediaSource source) {
        return source == MediaSource.OWN_TENCENT ? "tj.tencent.own-vod" : "tj.tencent.vod";
    }

    /** 生成密钥摘要，仅用于核对 Nacos 实际生效值，禁止记录明文密钥。 */
    private String urlKeyFingerprint(String urlKey) {
        return DigestUtil.sha256Hex(urlKey).substring(0, 12);
    }
    @Override
    public Long getVodAppId(MediaSource source) {
        return vodAppId(source);
    }

    private Long vodAppId(MediaSource source) {
        Long appId = source == MediaSource.OWN_TENCENT
                ? ownVodProperties().getAppId()
                : tencentProperties.getAppId();
        if (appId == null || appId <= 0) {
            throw new CommonException(String.format(
                    "%s VOD APPID 未配置，请检查 %s.appId",
                    source.getDesc(), vodConfigPrefix(source)));
        }
        return appId;
    }

}
