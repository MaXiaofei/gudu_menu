package com.gudu.xsd.modules.contentsecurity.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 微信消息推送签名校验（明文模式）：signature = SHA1(字典序拼接(token, timestamp, nonce))。
 * 另加时间窗（默认 ±5 分钟）限制重放；token 未配置时一律拒绝（不能让空 token 的签名通过）。
 */
public final class WxPushSignature {

    public static final long MAX_SKEW_SECONDS = 300;

    private WxPushSignature() {
    }

    public static boolean verify(String token, String timestamp, String nonce, String signature, long nowEpochSeconds) {
        if (token == null || token.isBlank() || timestamp == null || nonce == null || signature == null) return false;
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > MAX_SKEW_SECONDS) return false;
        String expected = sign(token, timestamp, nonce);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    public static String sign(String token, String timestamp, String nonce) {
        String[] parts = {token, timestamp, nonce};
        Arrays.sort(parts);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
