package com.gudu.xsd.modules.family.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/** 邀请码：8 位大写字母数字（去掉易混淆的 0/O/1/I），SecureRandom 生成。 */
public record InviteCode(String value) {

    static final int LENGTH = 8;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Pattern FORMAT = Pattern.compile("^[A-Z2-9]{8}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public InviteCode {
        if (value == null || !FORMAT.matcher(value).matches()) throw new DomainException("邀请码无效");
    }

    public static InviteCode random() {
        char[] c = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) c[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        return new InviteCode(new String(c));
    }

    /** 用户输入：忽略大小写与空白；格式不对抛「邀请码无效」。 */
    public static InviteCode parse(String raw) {
        return new InviteCode(raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
