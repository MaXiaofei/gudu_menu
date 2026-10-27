package com.gudu.xsd.common.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 演示数据造数脚本（scripts/demo-data/gen_demo_seed.py）的健康档案加密格式一致性：
 * Python 用测试密钥产出的密文 fixture，必须能被 Java {@link FieldCipher} 原样解出（格式漂移时这里先红）。
 * 重新生成 fixture：GUDU_FIELD_CRYPTO_KEYS=1:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY= \
 *   python3 scripts/demo-data/gen_demo_seed.py --crypto-fixture menu-api/src/test/resources/crypto/demo-seed-health-cipher.json
 */
class DemoSeedCipherFixtureTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode fixture() throws Exception {
        try (InputStream in = DemoSeedCipherFixtureTest.class.getResourceAsStream("/crypto/demo-seed-health-cipher.json")) {
            assertThat(in).as("fixture 缺失").isNotNull();
            return JSON.readTree(in);
        }
    }

    private static FieldCipher cipher(JsonNode f) {
        return FieldCipher.fromConfig(f.get("keys").asText(), f.get("active").asInt(), "hmac-key-hmac-key-hmac-key-01234");
    }

    @Test
    void python密文能被FieldCipher解出原文() throws Exception {
        JsonNode f = fixture();
        String cipherText = f.get("cipher").asText();
        assertThat(cipherText).startsWith("ENC:v1:");
        assertThat(FieldCipher.isEncrypted(cipherText)).isTrue();
        assertThat(FieldCipher.versionOf(cipherText)).isEqualTo(1);
        assertThat(cipher(f).decrypt(cipherText)).isEqualTo(f.get("plain").asText());
    }

    @Test
    void 解出的明文是合法健康档案JSON() throws Exception {
        JsonNode f = fixture();
        String plain = cipher(f).decrypt(f.get("cipher").asText());
        @SuppressWarnings("unchecked")
        Map<String, Object> m = JSON.readValue(plain, Map.class);
        assertThat(m).containsEntry("gender", "male").containsEntry("height", 178).containsEntry("weight", 76);
    }

    @Test
    void 密文被篡改时解密失败() throws Exception {
        JsonNode f = fixture();
        String c = f.get("cipher").asText();
        String tampered = c.substring(0, c.length() - 6) + (c.charAt(c.length() - 6) == 'A' ? "B" : "A") + c.substring(c.length() - 5);
        assertThatThrownBy(() -> cipher(f).decrypt(tampered)).isInstanceOf(FieldCryptoException.class);
    }
}
