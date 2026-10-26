package com.gudu.xsd.common.upload;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 上传文件短时签名 URL（A.3）：签名格式、缩略图共用签名、过期、篡改、外站域名、多图字段、入参验签。 */
class UploadUrlSignerTest {

    static final String SECRET = "unit-test-upload-secret-01";
    static final Instant NOW = Instant.parse("2026-10-26T03:20:00Z");
    static final String IMG = "/gudu/uploads/original/20261026/1767225600123.jpg";
    static final Pattern SIGNED = Pattern.compile("^(.*)\\?e=(\\d+)&s=([A-Za-z0-9_\\-]+)$");

    static UploadUrlSigner signer(Instant now, boolean enforce) {
        return new UploadUrlSigner(SECRET, Duration.ofHours(24), Duration.ofDays(7), enforce,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    static String[] parts(String signed) {
        Matcher m = SIGNED.matcher(signed);
        assertThat(m.matches()).as("签名格式 " + signed).isTrue();
        return new String[]{m.group(1), m.group(2), m.group(3)};
    }

    static String rel(String url) {
        return url.replaceFirst("^.*?/uploads/", "");
    }

    @Test
    void 签名格式_过期取整到整点_同一小时内URL稳定() {
        String a = signer(NOW, true).sign(IMG);
        String b = signer(NOW.plusSeconds(20 * 60), true).sign(IMG);
        String[] p = parts(a);
        assertThat(p[0]).isEqualTo(IMG);
        long e = Long.parseLong(p[1]);
        assertThat(e % 3600).isZero();
        assertThat(e).isBetween(NOW.getEpochSecond() + 24 * 3600, NOW.getEpochSecond() + 25 * 3600);
        assertThat(b).as("同一小时内签出相同地址，便于缓存").isEqualTo(a);
    }

    @Test
    void 验签_原图与缩略图共用签名() {
        UploadUrlSigner s = signer(NOW, true);
        String[] p = parts(s.sign(IMG));
        assertThat(s.verifyRequest(rel(IMG), p[1], p[2])).isTrue();
        // 前端把 /original/ 换成 /thumbnail/ 取缩略图，签名仍有效
        assertThat(s.verifyRequest(rel(IMG).replace("original/", "thumbnail/"), p[1], p[2])).isTrue();
    }

    @Test
    void 验签_篡改路径_篡改过期_缺参数_过期_均拒绝() {
        UploadUrlSigner s = signer(NOW, true);
        String[] p = parts(s.sign(IMG));
        assertThat(s.verifyRequest("original/20261026/1767225600124.jpg", p[1], p[2])).as("换文件名").isFalse();
        assertThat(s.verifyRequest(rel(IMG), String.valueOf(Long.parseLong(p[1]) + 3600), p[2])).as("延长过期").isFalse();
        assertThat(s.verifyRequest(rel(IMG), null, null)).as("未签名（存量地址也必须签名）").isFalse();
        assertThat(s.verifyRequest(rel(IMG), p[1], p[2] + "x")).isFalse();
        assertThat(s.verifyRequest("original/../../etc/passwd", p[1], p[2])).isFalse();
        UploadUrlSigner later = signer(Instant.ofEpochSecond(Long.parseLong(p[1]) + 1), true);
        assertThat(later.verifyRequest(rel(IMG), p[1], p[2])).as("过期").isFalse();
    }

    @Test
    void 不同密钥签名互不通用() {
        String[] p = parts(signer(NOW, true).sign(IMG));
        UploadUrlSigner other = new UploadUrlSigner("another-secret-0123456789", Duration.ofHours(24), Duration.ZERO,
                true, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(other.verifyRequest(rel(IMG), p[1], p[2])).isFalse();
    }

    @Test
    void 只签本站上传地址_外站域名与普通文本原样() {
        UploadUrlSigner s = signer(NOW, true);
        assertThat(s.sign("https://evil.com/uploads/original/20261026/a.jpg"))
                .as("外站 /uploads 不能被签出对本站同路径有效的签名").isEqualTo("https://evil.com/uploads/original/20261026/a.jpg");
        assertThat(s.sign("https://imxf.cloud/images/dish/1.jpg")).isEqualTo("https://imxf.cloud/images/dish/1.jpg");
        assertThat(s.signAll("番茄炒蛋")).isEqualTo("番茄炒蛋");
        assertThat(s.signAll("看 /gudu/uploads/a.jpg 这张")).isEqualTo("看 /gudu/uploads/a.jpg 这张");
        assertThat(parts(s.sign("https://imxf.cloud/gudu/uploads/original/20261026/a.jpg"))[0])
                .isEqualTo("https://imxf.cloud/gudu/uploads/original/20261026/a.jpg");
        assertThat(parts(s.sign("/uploads/e2e/a.jpg"))[0]).isEqualTo("/uploads/e2e/a.jpg");
    }

    @Test
    void 已签名地址重签_替换旧签名() {
        String old = signer(NOW, true).sign(IMG);
        String renewed = signer(NOW.plus(Duration.ofDays(2)), true).sign(old);
        assertThat(parts(renewed)[0]).isEqualTo(IMG);
        assertThat(renewed).isNotEqualTo(old);
    }

    @Test
    void 多图字段_逗号分隔逐个签名与还原() {
        UploadUrlSigner s = signer(NOW, true);
        String images = IMG + ",/gudu/uploads/original/20261026/b.png";
        String signed = s.signAll(images);
        String[] each = signed.split(",");
        assertThat(each).hasSize(2);
        assertThat(parts(each[1])[0]).isEqualTo("/gudu/uploads/original/20261026/b.png");
        assertThat(s.acceptAll(signed)).isEqualTo(images);
    }

    @Test
    void 入参_有效签名剥掉_未签名或伪造拒绝_过期宽限内接受() {
        UploadUrlSigner s = signer(NOW, true);
        assertThat(s.accept(s.sign(IMG))).isEqualTo(IMG);
        assertThatThrownBy(() -> s.accept(IMG)).as("未签名：可能是猜出来的他人文件").isInstanceOf(UploadUrlRejectedException.class);
        assertThatThrownBy(() -> s.accept(IMG + "?e=9999999999&s=AAAAAAAAAAAAAAAAAAAAAA"))
                .isInstanceOf(UploadUrlRejectedException.class);
        String signed = s.sign(IMG);
        long e = Long.parseLong(parts(signed)[1]);
        assertThat(signer(Instant.ofEpochSecond(e + Duration.ofDays(6).toSeconds()), true).accept(signed)).isEqualTo(IMG);
        assertThatThrownBy(() -> signer(Instant.ofEpochSecond(e + Duration.ofDays(8).toSeconds()), true).accept(signed))
                .isInstanceOf(UploadUrlRejectedException.class);
        assertThat(s.accept("https://imxf.cloud/images/a.jpg")).as("非上传地址不管").isEqualTo("https://imxf.cloud/images/a.jpg");
    }

    @Test
    void 校验关闭_访问与入参都放行_但仍签名() {
        UploadUrlSigner s = signer(NOW, false);
        assertThat(s.verifyRequest(rel(IMG), null, null)).isTrue();
        assertThat(s.accept(IMG)).isEqualTo(IMG);
        assertThat(s.accept(s.sign(IMG))).isEqualTo(IMG);
        assertThat(s.sign(IMG)).contains("?e=");
    }

    @Test
    void 未配置密钥_不签名_开启校验则启动失败() {
        UploadUrlSigner none = new UploadUrlSigner("", Duration.ofHours(24), Duration.ZERO, false, Clock.systemUTC());
        assertThat(none.signing()).isFalse();
        assertThat(none.sign(IMG)).isEqualTo(IMG);
        assertThatThrownBy(() -> new UploadUrlSigner("", Duration.ofHours(24), Duration.ZERO, true, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new UploadUrlSigner("short", Duration.ofHours(24), Duration.ZERO, true, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 自定义有效期_用于微信拉图() {
        String[] p = parts(signer(NOW, true).sign(IMG, Duration.ofHours(2)));
        assertThat(Long.parseLong(p[1])).isBetween(NOW.getEpochSecond() + 2 * 3600, NOW.getEpochSecond() + 3 * 3600);
    }
}
