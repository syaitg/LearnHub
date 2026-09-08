package com.tianji.media.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.qcloud.vod.VodUploadClient;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tianji.media.storage.IFileStorage;
import com.tianji.media.storage.IMediaStorage;
import com.tianji.media.storage.tencent.TencentFileStorage;
import com.tianji.media.storage.tencent.TencentMediaStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties({TencentProperties.class})
public class TencentConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
    public VodClient tencentVodClient(TencentProperties properties){
        // 1.授权信息
        String secretId = requireText(properties.getSecretId(),
                "黑马官方 VOD 未配置 SecretId：tj.tencent.secretId");
        String secretKey = requireText(properties.getSecretKey(),
                "黑马官方 VOD 未配置 SecretKey：tj.tencent.secretKey");
        Credential cred = new Credential(secretId, secretKey);
        // 2.配置超时时间
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setConnTimeout(1);
        httpProfile.setReadTimeout(10);
        httpProfile.setWriteTimeout(10);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        // 2.初始化客户端
        return new VodClient(cred, requireOfficialVod(properties).getRegion(), clientProfile);
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
    public VodUploadClient tencentVodUploadClient(TencentProperties properties){
        // 1.初始化客户端
        return new VodUploadClient(
                requireText(properties.getSecretId(), "黑马官方 VOD 未配置 SecretId：tj.tencent.secretId"),
                requireText(properties.getSecretKey(), "黑马官方 VOD 未配置 SecretKey：tj.tencent.secretKey"));
    }

    @Bean(name = "ownTencentVodClient")
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
    public VodClient ownTencentVodClient(TencentProperties properties){
        TencentProperties.VodProperties ownVod = properties.getOwnVod();
        if (ownVod == null) {
            throw new IllegalStateException(
                    "未配置自有腾讯云 VOD，请在 Nacos 的 media-service.yaml 中配置 tj.tencent.own-vod");
        }
        if (!org.springframework.util.StringUtils.hasText(ownVod.getSecretId())) {
            throw new IllegalStateException("自有腾讯云 VOD 未配置 SecretId：tj.tencent.own-vod.secretId");
        }
        if (!org.springframework.util.StringUtils.hasText(ownVod.getSecretKey())) {
            throw new IllegalStateException("自有腾讯云 VOD 未配置 SecretKey：tj.tencent.own-vod.secretKey");
        }
        if (!ownVod.isEnable()) {
            throw new IllegalStateException("自有腾讯云 VOD 未启用，请将 tj.tencent.own-vod.enable 配置为 true");
        }
        if (ownVod.getAppId() == null || ownVod.getAppId() <= 0) {
            throw new IllegalStateException("自有腾讯云 VOD 未配置 APPID：tj.tencent.own-vod.appId");
        }
        if (!org.springframework.util.StringUtils.hasText(ownVod.getRegion())) {
            throw new IllegalStateException("自有腾讯云 VOD 未配置地域：tj.tencent.own-vod.region");
        }
        Credential cred = new Credential(ownVod.getSecretId(), ownVod.getSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setConnTimeout(1);
        httpProfile.setReadTimeout(10);
        httpProfile.setWriteTimeout(10);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        return new VodClient(cred, ownVod.getRegion(), clientProfile);
    }
    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
    public IMediaStorage tencentMediaStorage(
            @Qualifier("tencentVodClient") VodClient officialVodClient,
            @Qualifier("ownTencentVodClient") VodClient ownVodClient,
            TencentProperties properties){
        return new TencentMediaStorage(officialVodClient, ownVodClient, properties);
    }

    @Bean(name = {"tencentCosClient", "officialTencentCosClient"})
    @Primary
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public COSClient officialTencentCosClient(TencentProperties properties){
        TencentProperties.CosProperties officialCos = requireCos(
                officialCosProperties(properties), "tj.tencent.cos", false);
        return createCosClient(officialCos,
                requireText(properties.getSecretId(), "黑马官方 COS 未配置 SecretId：tj.tencent.secretId"),
                requireText(properties.getSecretKey(), "黑马官方 COS 未配置 SecretKey：tj.tencent.secretKey"));
    }

    @Bean(name = "ownTencentCosClient")
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public COSClient ownTencentCosClient(TencentProperties properties){
        TencentProperties.CosProperties ownCos = requireCos(
                properties.getOwnCos(), "tj.tencent.own-cos", true);
        return createCosClient(ownCos,
                requireText(ownCos.getSecretId(), "自有 COS 未配置 SecretId：tj.tencent.own-cos.secretId"),
                requireText(ownCos.getSecretKey(), "自有 COS 未配置 SecretKey：tj.tencent.own-cos.secretKey"));
    }

    @Bean(name = {"transferManager", "officialTransferManager"})
    @Primary
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public TransferManager officialTransferManager(
            @Qualifier("officialTencentCosClient") COSClient officialCosClient,
            TencentProperties properties){
        return createTransferManager(officialCosClient, officialCosProperties(properties));
    }

    @Bean(name = "ownTransferManager")
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public TransferManager ownTransferManager(
            @Qualifier("ownTencentCosClient") COSClient ownCosClient,
            TencentProperties properties){
        return createTransferManager(ownCosClient, ownCosProperties(properties));
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public IFileStorage tencentFileStorage(
            @Qualifier("officialTencentCosClient") COSClient officialCosClient,
            @Qualifier("ownTencentCosClient") COSClient ownCosClient,
            @Qualifier("officialTransferManager") TransferManager officialTransferManager,
            @Qualifier("ownTransferManager") TransferManager ownTransferManager,
            TencentProperties properties){
        return new TencentFileStorage(
                officialCosClient, ownCosClient,
                officialTransferManager, ownTransferManager,
                properties);
    }

    /** 获取官方 COS 配置；原有 cos 节点就是官方 COS。 */
    private TencentProperties.CosProperties officialCosProperties(TencentProperties properties) {
        return properties.getCos();
    }

    /** 获取自有 COS 配置；自有账号必须独立配置，禁止静默回退到官方账号。 */
    private TencentProperties.CosProperties ownCosProperties(TencentProperties properties) {
        return properties.getOwnCos();
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
        requireText(cos.getRegion(), configPrefix + ".region 未配置");
        requireText(cos.getBucket(), configPrefix + ".bucket 未配置");
        requirePositive(cos.getMultipartUploadThreshold(),
                configPrefix + ".multipartUploadThreshold 必须大于 0");
        requirePositive(cos.getMinimumUploadPartSize(),
                configPrefix + ".minimumUploadPartSize 必须大于 0");
        if (ownAccount) {
            requireAppId(cos.getAppId(), configPrefix + ".appId 未配置或无效");
        }
        return cos;
    }

    private COSClient createCosClient(TencentProperties.CosProperties cos,
                                      String secretId, String secretKey) {
        // 1.授权信息
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 2.基本配置
        Region region = new Region(cos.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        // 3.初始化客户端
        return new COSClient(cred, clientConfig);
    }

    private TencentProperties.VodProperties requireOfficialVod(TencentProperties properties) {
        TencentProperties.VodProperties vod = properties.getVod();
        if (vod == null) {
            throw new IllegalStateException("未配置黑马官方腾讯云 VOD 参数：tj.tencent.vod");
        }
        if (!vod.isEnable()) {
            throw new IllegalStateException("黑马官方腾讯云 VOD 未启用，请将 tj.tencent.vod.enable 配置为 true");
        }
        requireText(vod.getRegion(), "黑马官方 VOD 未配置地域：tj.tencent.vod.region");
        requirePositive(vod.getVodValidSeconds(),
                "黑马官方 VOD 签名有效期必须大于 0：tj.tencent.vod.vodValidSeconds");
        return vod;
    }

    private String requireText(String value, String message) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private Long requireAppId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private void requirePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalStateException(message);
        }
    }

    private TransferManager createTransferManager(COSClient cosClient,
                                                  TencentProperties.CosProperties cos) {
        // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        ExecutorService threadPool = Executors.newFixedThreadPool(4);

        // 传入一个 threadPool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        TransferManager transferManager = new TransferManager(cosClient, threadPool);

        // 设置高级接口的配置项
        // 分块上传阈值和分块大小分别为 5MB 和 1MB
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(cos.getMultipartUploadThreshold());
        transferManagerConfiguration.setMinimumUploadPartSize(cos.getMinimumUploadPartSize());
        transferManager.setConfiguration(transferManagerConfiguration);

        return transferManager;
    }

}
