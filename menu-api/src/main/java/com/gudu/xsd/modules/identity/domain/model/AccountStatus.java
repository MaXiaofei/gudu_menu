package com.gudu.xsd.modules.identity.domain.model;

/** 账号状态：正常 / 已注销（匿名化） / 已并入其他账号（微信账号并入手机号账号后）。 */
public enum AccountStatus {
    ACTIVE, DEACTIVATED, MERGED;

    public static AccountStatus of(String s) {
        if (s == null || s.isBlank()) return ACTIVE;
        return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
