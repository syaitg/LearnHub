package com.tianji.media.storage.tencent;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.jwt.JWT;
import com.tianji.common.exceptions.CommonException;
import com.tianji.media.config.TencentProperties;
import com.tianji.media.enums.MediaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 腾讯云双 VOD 账号播放地址和播放签名测试。
 */
class TencentMediaStorageTest {

    private static final String OWN_MEDIA_URL =
            "https://1479043174.vod-qcloud.com/22fe010avodcq1479043174/"
                    + "76a35c605001834818840207961/3g9dA5Gm1NcA.mp4";

    @Test
    @DisplayName("自有媒资应使用 urlKey 生成 t 和 sign 播放地址")
    void shouldGenerateOwnAntiLeechUrl() {
        TencentProperties properties = properties();
        TencentProperties.VodProperties ownVod = ownVod();
        properties.setOwnVod(ownVod);
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        String playUrl = storage.getPlayUrl(OWN_MEDIA_URL, null, MediaSource.OWN_TENCENT);
        Matcher matcher = Pattern.compile("\\?t=([0-9a-f]+)&sign=([0-9a-f]{32})$").matcher(playUrl);

        assertThat(matcher.find()).isTrue();
        String t = matcher.group(1);
        String sign = matcher.group(2);
        String directory = "/22fe010avodcq1479043174/76a35c605001834818840207961/";
        assertThat(Long.parseLong(t, 16)).isGreaterThan(System.currentTimeMillis() / 1000);
        assertThat(sign).isEqualTo(DigestUtil.md5Hex("ownurlkey" + directory + t));
        assertThat(playUrl).startsWith(OWN_MEDIA_URL + "?t=");
    }

    @Test
    @DisplayName("腾讯云官方示例的 t 和 sign 应按 urlKey、目录、t 计算")
    void shouldMatchTencentOfficialKeyAntiLeechExample() {
        String directory = "/22fe010avodcq1479043174/76a35c605001834818840207961/";
        String t = "5af3107a4000";

        // 腾讯云 Key 防盗链的签名原文：urlKey + 视频目录 + t。
        String sign = DigestUtil.md5Hex("VLTreeZv5QkKrtnZOkOx" + directory + t);

        assertThat(sign).isEqualTo("687f4602bea6f274c5fcef896ee4a636");
    }

    @Test
    @DisplayName("自有媒资试看时应按 t、exper、sign 顺序生成参数")
    void shouldGenerateOwnAntiLeechUrlWithPreviewDuration() {
        TencentProperties properties = properties();
        properties.setOwnVod(ownVod());
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        String playUrl = storage.getPlayUrl(OWN_MEDIA_URL, 5, MediaSource.OWN_TENCENT);
        Matcher matcher = Pattern.compile("\\?t=([0-9a-f]+)&exper=300&sign=([0-9a-f]{32})$").matcher(playUrl);

        assertThat(matcher.find()).isTrue();
        String t = matcher.group(1);
        String directory = "/22fe010avodcq1479043174/76a35c605001834818840207961/";
        assertThat(matcher.group(2)).isEqualTo(
                DigestUtil.md5Hex("ownurlkey" + directory + t + "300"));
    }

    @Test
    @DisplayName("reject invalid anti-leech key")
    void shouldRejectInvalidAntiLeechKey() {
        TencentProperties properties = properties();
        TencentProperties.VodProperties ownVod = ownVod();
        ownVod.setUrlKey("bad-key");
        properties.setOwnVod(ownVod);
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        assertThatThrownBy(() -> storage.getPlayUrl(OWN_MEDIA_URL, null, MediaSource.OWN_TENCENT))
                .isInstanceOf(CommonException.class)
                .hasMessage(String.format("%s anti-leech Key must contain 8-20 letters or digits; check Nacos config tj.tencent.own-vod.urlKey", MediaSource.OWN_TENCENT.getDesc()));
    }

    @Test
    @DisplayName("自有媒资缺少 urlKey 时应提示准确的 Nacos 配置项")
    void shouldRejectOwnAntiLeechUrlWithoutUrlKey() {
        TencentProperties properties = properties();
        TencentProperties.VodProperties ownVod = ownVod();
        ownVod.setUrlKey(null);
        properties.setOwnVod(ownVod);
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        assertThatThrownBy(() -> storage.getPlayUrl(OWN_MEDIA_URL, null, MediaSource.OWN_TENCENT))
                .isInstanceOf(CommonException.class)
                .hasMessage("自有腾讯云未配置防盗链 Key，请在 Nacos 的 media-service.yaml 中配置 "
                        + "tj.tencent.own-vod.urlKey");
    }

    @Test
    @DisplayName("自有媒资有效期未配置时应拒绝生成播放地址")
    void shouldRejectOwnAntiLeechUrlWithoutValidSeconds() {
        TencentProperties properties = properties();
        TencentProperties.VodProperties ownVod = ownVod();
        ownVod.setVodValidSeconds(0L);
        properties.setOwnVod(ownVod);
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        assertThatThrownBy(() -> storage.getPlayUrl(OWN_MEDIA_URL, null, MediaSource.OWN_TENCENT))
                .isInstanceOf(CommonException.class)
                .hasMessage("自有腾讯云未配置有效的防盗链过期时间，请检查 Nacos 配置 "
                        + "tj.tencent.own-vod.vodValidSeconds");
    }

    @Test
    @DisplayName("原始地址包含查询参数时应拒绝生成第二套参数")
    void shouldRejectOriginUrlWithQueryString() {
        TencentProperties properties = properties();
        properties.setOwnVod(ownVod());
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        assertThatThrownBy(() -> storage.getPlayUrl(
                OWN_MEDIA_URL + "?foo=bar", null, MediaSource.OWN_TENCENT))
                .isInstanceOf(CommonException.class)
                .hasMessage("视频原始播放地址已包含查询参数，无法生成统一的防盗链播放地址");
    }

    @Test
    @DisplayName("免费试看时长为零时不能退化为完整视频播放")
    void shouldRejectZeroPreviewDuration() {
        TencentProperties properties = properties();
        properties.setOwnVod(ownVod());
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        assertThatThrownBy(() -> storage.getPlayUrl(OWN_MEDIA_URL, 0, MediaSource.OWN_TENCENT))
                .isInstanceOf(CommonException.class)
                .hasMessage("免费试看时长必须大于 0 分钟，无法生成防盗链播放地址");
    }

    @Test
    @DisplayName("自有媒资不应再生成 psign")
    void shouldNotGeneratePsignForOwnVod() {
        TencentProperties properties = properties();
        properties.setOwnVod(ownVod());
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        assertThatThrownBy(() -> storage.getPlaySignature(
                "own-file-id", 1L, null, MediaSource.OWN_TENCENT))
                .isInstanceOf(CommonException.class)
                .hasMessage("自有腾讯云媒资统一使用 Key 防盗链播放地址，不能生成 psign");
    }

    @Test
    @DisplayName("官方媒资仍应保留原有 psign 结构")
    void shouldKeepOfficialSignatureStructure() {
        TencentProperties properties = properties();
        TencentMediaStorage storage = new TencentMediaStorage(null, null, properties);

        String signature = storage.getPlaySignature(
                "official-file-id", 1L, null, MediaSource.OFFICIAL_TENCENT);

        assertThat(decodePayload(signature)).doesNotContain("contentInfo");
        assertThat(decodePayload(signature)).contains("\"pcfg\":\"basicDrmPreset\"");
        assertThat(JWT.of(signature)
                .setKey("official-play-key".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .verify()).isTrue();
    }

    private static TencentProperties properties() {
        TencentProperties properties = new TencentProperties();
        properties.setAppId(1312394356L);
        TencentProperties.VodProperties officialVod = new TencentProperties.VodProperties();
        officialVod.setUrlKey("official-play-key");
        officialVod.setPfcg("basicDrmPreset");
        properties.setVod(officialVod);
        return properties;
    }

    private static TencentProperties.VodProperties ownVod() {
        TencentProperties.VodProperties ownVod = new TencentProperties.VodProperties();
        ownVod.setAppId(1479043174L);
        ownVod.setUrlKey("ownurlkey");
        ownVod.setVodValidSeconds(7776000L);
        ownVod.setPfcg("");
        return ownVod;
    }

    private static String decodePayload(String signature) {
        String[] parts = signature.split("\\.");
        return new String(Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
