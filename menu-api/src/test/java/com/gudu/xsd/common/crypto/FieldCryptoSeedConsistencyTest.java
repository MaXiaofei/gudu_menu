package com.gudu.xsd.common.crypto;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * e2e-seed.sql 用 MySQL 表达式 SHA2(k^opad ‖ UNHEX(SHA2(k^ipad ‖ m))) 计算手机号盲索引（MySQL 无 HMAC 函数），
 * 其中 k^ipad / k^opad 常量由 application-test.yml 的测试 HMAC 密钥推导。本测试保证三者一致：
 * 改测试密钥而忘了改种子常量会在这里失败，而不是 E2E 里莫名其妙登录不上。
 */
class FieldCryptoSeedConsistencyTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = FieldCryptoSeedConsistencyTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String match(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertThat(m.find()).as(regex).isTrue();
        return m.group(1);
    }

    @Test
    void 种子里的HMAC常量与测试密钥一致_SQL公式等于FieldCipher盲索引() throws Exception {
        String yml = resource("application-test.yml");
        String hmacKey = match(yml, "hmac-key: \\$\\{GUDU_FIELD_CRYPTO_HMAC_KEY:([^}]+)}");
        String keys = match(yml, "keys: \\$\\{GUDU_FIELD_CRYPTO_KEYS:([^}]+)}");
        String seed = resource("e2e-seed.sql");
        byte[] ipad = HexFormat.of().parseHex(match(seed, "@e2e_hk_i := UNHEX\\('([0-9a-f]+)'\\)"));
        byte[] opad = HexFormat.of().parseHex(match(seed, "@e2e_hk_o := UNHEX\\('([0-9a-f]+)'\\)"));

        byte[] k = new byte[64];
        byte[] raw = hmacKey.getBytes(StandardCharsets.UTF_8);
        assertThat(raw.length).as("测试 HMAC 密钥须 ≤ 64 字节（否则 HMAC 先哈希密钥，种子公式不成立）").isLessThanOrEqualTo(64);
        System.arraycopy(raw, 0, k, 0, raw.length);
        byte[] expectI = new byte[64];
        byte[] expectO = new byte[64];
        for (int i = 0; i < 64; i++) {
            expectI[i] = (byte) (k[i] ^ 0x36);
            expectO[i] = (byte) (k[i] ^ 0x5c);
        }
        assertThat(ipad).isEqualTo(expectI);
        assertThat(opad).isEqualTo(expectO);

        FieldCipher cipher = FieldCipher.fromConfig(keys, 1, hmacKey);
        for (String phone : new String[]{"13900000088", "13800000001", "admin", "13900009999"}) {
            MessageDigest inner = MessageDigest.getInstance("SHA-256");
            inner.update(ipad);
            byte[] ih = inner.digest(phone.getBytes(StandardCharsets.UTF_8));
            MessageDigest outer = MessageDigest.getInstance("SHA-256");
            outer.update(opad);
            String sqlFormula = HexFormat.of().formatHex(outer.digest(ih));
            assertThat(sqlFormula).as(phone).isEqualTo(cipher.blindIndex(phone));
        }
    }

    @Test
    void dev与test使用同一套固定测试密钥() throws Exception {
        String dev = resource("application-dev.yml");
        String test = resource("application-test.yml");
        String re = "(keys: \\$\\{GUDU_FIELD_CRYPTO_KEYS:[^}]+}|hmac-key: \\$\\{GUDU_FIELD_CRYPTO_HMAC_KEY:[^}]+})";
        Matcher d = Pattern.compile(re).matcher(dev);
        Matcher t = Pattern.compile(re).matcher(test);
        while (d.find()) {
            assertThat(t.find()).isTrue();
            assertThat(d.group(1)).isEqualTo(t.group(1));
        }
    }
}
