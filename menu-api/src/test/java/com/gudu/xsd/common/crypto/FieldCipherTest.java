package com.gudu.xsd.common.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FieldCipherTest {

    public static final String K1 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    public static final String K2 = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
    public static final String HMAC = "hmac-key-hmac-key-hmac-key-01234";

    public static FieldCipher v1() {
        return FieldCipher.fromConfig("1:" + K1, 1, HMAC);
    }

    @Test
    void 加解密往返_随机IV_带版本前缀() {
        FieldCipher c = v1();
        String a = c.encrypt("13800000001");
        String b = c.encrypt("13800000001");
        assertThat(a).startsWith("ENC:v1:").isNotEqualTo(b);
        assertThat(c.decrypt(a)).isEqualTo("13800000001");
        assertThat(c.decrypt(b)).isEqualTo("13800000001");
        assertThat(c.encrypt("{\"height\":170,\"audiences\":[\"高血压\"]}")).satisfies(x ->
                assertThat(c.decrypt(x)).isEqualTo("{\"height\":170,\"audiences\":[\"高血压\"]}"));
    }

    @Test
    void null与存量明文原样返回_已是密文不重复加密() {
        FieldCipher c = v1();
        assertThat(c.encrypt(null)).isNull();
        assertThat(c.decrypt(null)).isNull();
        assertThat(c.decrypt("13800000001")).isEqualTo("13800000001");
        assertThat(c.decrypt("{\"a\":1}")).isEqualTo("{\"a\":1}");
        String enc = c.encrypt("x");
        assertThat(c.encrypt(enc)).isEqualTo(enc);
    }

    @Test
    void 密钥轮换_旧版本密文仍可解_新数据用新版本_needsReencrypt() {
        String old = v1().encrypt("13800000001");
        FieldCipher rotated = FieldCipher.fromConfig("1:" + K1 + ",2:" + K2, 2, HMAC);
        assertThat(rotated.decrypt(old)).isEqualTo("13800000001");
        String fresh = rotated.encrypt("13800000001");
        assertThat(fresh).startsWith("ENC:v2:");
        assertThat(FieldCipher.versionOf(fresh)).isEqualTo(2);
        assertThat(rotated.needsReencrypt(old)).isTrue();
        assertThat(rotated.needsReencrypt(fresh)).isFalse();
        assertThat(rotated.needsReencrypt("13800000001")).isTrue();
        assertThat(rotated.needsReencrypt(null)).isFalse();
    }

    @Test
    void 缺版本密钥_篡改_错误密钥_一律抛异常不返回错误数据() {
        String enc = v1().encrypt("13800000001");
        FieldCipher onlyV2 = FieldCipher.fromConfig("2:" + K2, 2, HMAC);
        assertThatThrownBy(() -> onlyV2.decrypt(enc)).isInstanceOf(FieldCryptoException.class).hasMessageContaining("v1");
        FieldCipher wrongKey = FieldCipher.fromConfig("1:" + K2, 1, HMAC);
        assertThatThrownBy(() -> wrongKey.decrypt(enc)).isInstanceOf(FieldCryptoException.class);
        char[] chars = enc.toCharArray();
        chars[chars.length - 3] = chars[chars.length - 3] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> v1().decrypt(new String(chars))).isInstanceOf(FieldCryptoException.class);
        assertThatThrownBy(() -> v1().decrypt("ENC:vx:abc")).isInstanceOf(FieldCryptoException.class);
    }

    @Test
    void 盲索引_确定性_等于HMAC_SHA256_去首尾空格() throws Exception {
        FieldCipher c = v1();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal("13800000001".getBytes(StandardCharsets.UTF_8)));
        assertThat(c.blindIndex("13800000001")).isEqualTo(expected).hasSize(64);
        assertThat(c.blindIndex(" 13800000001 ")).isEqualTo(expected);
        assertThat(c.blindIndex("13800000002")).isNotEqualTo(expected);
        assertThat(c.blindIndex(null)).isNull();
        assertThat(c.blindIndex(" ")).isNull();
        // 盲索引不随加密密钥轮换
        assertThat(FieldCipher.fromConfig("1:" + K1 + ",2:" + K2, 2, HMAC).blindIndex("13800000001")).isEqualTo(expected);
    }

    @Test
    void 配置校验_缺密钥_长度不对_active不存在_hmac过短_格式错误() {
        assertThatThrownBy(() -> FieldCipher.fromConfig("", 1, HMAC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldCipher.fromConfig(null, 1, HMAC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldCipher.fromConfig("1:" + Base64.getEncoder().encodeToString(new byte[16]), 1, HMAC))
                .hasMessageContaining("32 字节");
        assertThatThrownBy(() -> FieldCipher.fromConfig("1:" + K1, 2, HMAC)).hasMessageContaining("active");
        assertThatThrownBy(() -> FieldCipher.fromConfig("1:" + K1, 1, "short")).hasMessageContaining("32 字节");
        assertThatThrownBy(() -> FieldCipher.fromConfig("1:" + K1, 1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldCipher.fromConfig(K1, 1, HMAC)).hasMessageContaining("版本:base64");
        assertThatThrownBy(() -> FieldCipher.fromConfig("1:" + K1 + ",1:" + K2, 1, HMAC)).hasMessageContaining("重复");
        assertThatThrownBy(() -> FieldCipher.fromConfig("1:@@@", 1, HMAC)).hasMessageContaining("base64");
    }
}
