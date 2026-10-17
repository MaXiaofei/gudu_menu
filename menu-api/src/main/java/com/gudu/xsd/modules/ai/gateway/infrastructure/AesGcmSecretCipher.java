package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.gudu.xsd.modules.ai.gateway.domain.port.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM：主密钥 = SHA-256(GUDU_SECRET_KEY)；密文格式 {@code v1:base64(iv[12] | ciphertext+tag)}。
 * 主密钥缺失时 {@link #available()} 为 false，后台不允许保存 key。
 */
@Component
public class AesGcmSecretCipher implements SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(@Value("${GUDU_SECRET_KEY:}") String master) {
        this.key = master == null || master.isBlank() ? null : derive(master);
    }

    @Override
    public boolean available() {
        return key != null;
    }

    @Override
    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length)
                    .put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        requireKey();
        if (cipherText == null || !cipherText.startsWith(PREFIX)) throw new IllegalStateException("密文格式不正确");
        try {
            byte[] all = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            return new String(c.doFinal(all, IV_LEN, all.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("解密失败（主密钥不匹配或密文损坏）");
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("未配置 GUDU_SECRET_KEY");
    }

    private static SecretKeySpec derive(String master) {
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(master.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(k, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
