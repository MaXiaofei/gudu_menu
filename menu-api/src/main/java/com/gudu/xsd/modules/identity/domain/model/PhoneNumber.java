package com.gudu.xsd.modules.identity.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.util.regex.Pattern;

/**
 * 手机号（登录名）值对象。
 *
 * <p>{@link #mobile}：用户输入的新手机号，必须是 11 位大陆手机号；
 * {@link #rehydrate}：存量登录名（历史超管字面量 {@code admin} 等）从库里还原，只做非空与长度校验。
 */
public record PhoneNumber(String value) {

    static final int MAX_LENGTH = 32;
    private static final Pattern MOBILE = Pattern.compile("^1\\d{10}$");

    public PhoneNumber {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new DomainException("手机号格式不正确");
        }
        if (value.startsWith("wx_")) throw new DomainException("手机号格式不正确");
    }

    public static PhoneNumber mobile(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (!MOBILE.matcher(v).matches()) throw new DomainException("手机号格式不正确");
        return new PhoneNumber(v);
    }

    public static PhoneNumber rehydrate(String stored) {
        return stored == null || stored.isBlank() ? null : new PhoneNumber(stored);
    }

    /** 脱敏：138****0001；非 11 位手机号（如 admin）返回 null。 */
    public String masked() {
        if (!MOBILE.matcher(value).matches()) return null;
        return value.substring(0, 3) + "****" + value.substring(7);
    }
}
