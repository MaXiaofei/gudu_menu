package com.gudu.xsd.common.crypto;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** dev 与 test 两个 profile 须共用同一套固定测试密钥（共用同一个开发库，密文互相要能解开）。 */
class FieldCryptoSeedConsistencyTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = FieldCryptoSeedConsistencyTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
