package com.gudu.xsd.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 字段级加密（mini-release-compliance B.3）：AES-256-GCM + 密钥版本号；另提供 HMAC-SHA256 盲索引（手机号唯一与查询）。
 *
 * <p>密文格式：{@code ENC:v<版本>:<base64(12 字节 IV ‖ 密文 ‖ 16 字节 tag)>}。
 * <ul>
 *   <li>版本号写在密文里：解密按密文自带版本取密钥；加密总用 {@code activeVersion}。
 *       轮换 = 新增版本密钥并切 active，旧密钥保留用于解密，再跑一次迁移（FieldEncryptionMigrator）把旧版本密文重加密；</li>
 *   <li>不带 {@code ENC:} 前缀的值视为存量明文原样返回（迁移前 / 回滚后均可读）；</li>
 *   <li>盲索引 = hex(HMAC-SHA256(hmacKey, 规范化值))，不随加密密钥轮换（换 HMAC 密钥须停写后重算全部摘要）。</li>
 * </ul>
 * 纯 JDK 实现，线程安全。
 */
public final class FieldCipher {

    public static final String PREFIX = "ENC:v";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final int MIN_HMAC_KEY_BYTES = 32;

    private final Map<Integer, SecretKeySpec> keys;
    private final int activeVersion;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param keys          版本号 → 32 字节 AES 密钥
     * @param activeVersion 新数据加密使用的版本（必须存在于 keys）
     * @param hmacKey       盲索引密钥（≥ 32 字节）
     */
    public FieldCipher(Map<Integer, byte[]> keys, int activeVersion, byte[] hmacKey) {
        Objects.requireNonNull(keys, "keys");
        if (keys.isEmpty()) throw new IllegalArgumentException("字段加密密钥未配置（gudu.field-crypto.keys）");
        Map<Integer, SecretKeySpec> m = new LinkedHashMap<>();
        keys.forEach((v, k) -> {
            if (v == null || v < 1) throw new IllegalArgumentException("字段加密密钥版本号须为正整数：" + v);
            if (k == null || k.length != KEY_BYTES) {
                throw new IllegalArgumentException("字段加密密钥 v" + v + " 须为 32 字节（base64 编码后 44 字符）");
            }
            m.put(v, new SecretKeySpec(k, "AES"));
        });
        if (!m.containsKey(activeVersion)) {
            throw new IllegalArgumentException("active 版本 v" + activeVersion + " 不在已配置密钥中：" + m.keySet());
        }
        if (hmacKey == null || hmacKey.length < MIN_HMAC_KEY_BYTES) {
            throw new IllegalArgumentException("盲索引密钥（gudu.field-crypto.hmac-key）须 ≥ 32 字节");
        }
        this.keys = Map.copyOf(m);
        this.activeVersion = activeVersion;
        this.hmacKey = new SecretKeySpec(hmacKey, "HmacSHA256");
    }

    /**
     * 解析配置：{@code keys} 形如 {@code "1:<base64>,2:<base64>"}；hmacKey 为普通字符串（UTF-8，≥ 32 字节）。
     */
    public static FieldCipher fromConfig(String keys, int activeVersion, String hmacKey) {
        Map<Integer, byte[]> parsed = new LinkedHashMap<>();
        if (keys != null) {
            for (String part : keys.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                int colon = p.indexOf(':');
                if (colon <= 0) throw new IllegalArgumentException("字段加密密钥格式应为 版本:base64，多个用逗号分隔");
                int version;
                try {
                    version = Integer.parseInt(p.substring(0, colon).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("字段加密密钥版本号非法：" + p.substring(0, colon));
                }
                byte[] key;
                try {
                    key = Base64.getDecoder().decode(p.substring(colon + 1).trim());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("字段加密密钥 v" + version + " 不是合法 base64");
                }
                if (parsed.put(version, key) != null) {
                    throw new IllegalArgumentException("字段加密密钥版本重复：v" + version);
                }
            }
        }
        return new FieldCipher(parsed, activeVersion, hmacKey == null ? null : hmacKey.getBytes(StandardCharsets.UTF_8));
    }

    public int activeVersion() {
        return activeVersion;
    }

    /** 是否为本格式密文。 */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 密文的密钥版本；明文返回 0。 */
    public static int versionOf(String value) {
        if (!isEncrypted(value)) return 0;
        int colon = value.indexOf(':', PREFIX.length());
        if (colon < 0) throw new FieldCryptoException("密文格式错误");
        try {
            return Integer.parseInt(value.substring(PREFIX.length(), colon));
        } catch (NumberFormatException e) {
            throw new FieldCryptoException("密文版本号格式错误");
        }
    }

    /** 加密（null 原样返回）。已是密文的不重复加密。 */
    public String encrypt(String plain) {
        if (plain == null || isEncrypted(plain)) return plain;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, keys.get(activeVersion), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return PREFIX + activeVersion + ":" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new FieldCryptoException("字段加密失败", e);
        }
    }

    /** 解密（null / 存量明文原样返回）。密钥版本缺失或密文被篡改抛 {@link FieldCryptoException}。 */
    public String decrypt(String value) {
        if (!isEncrypted(value)) return value;
        int version = versionOf(value);
        SecretKeySpec key = keys.get(version);
        if (key == null) throw new FieldCryptoException("缺少字段加密密钥 v" + version + "（轮换后旧密钥不能删除）");
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(value.substring(value.indexOf(':', PREFIX.length()) + 1));
        } catch (IllegalArgumentException e) {
            throw new FieldCryptoException("密文格式错误");
        }
        if (raw.length <= IV_BYTES) throw new FieldCryptoException("密文长度错误");
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            byte[] plain = c.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new FieldCryptoException("字段解密失败（密钥不匹配或密文被篡改）", e);
        }
    }

    /** 是否需要（重）加密：明文，或非 active 版本的密文。 */
    public boolean needsReencrypt(String value) {
        if (value == null) return false;
        return !isEncrypted(value) || versionOf(value) != activeVersion;
    }

    /** 盲索引：hex(HMAC-SHA256)。null / 空白返回 null。值先 trim。 */
    public String blindIndex(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new FieldCryptoException("盲索引计算失败", e);
        }
    }
}
