package com.gudu.xsd.modules.identity.domain.model;

/** 平台角色（与家庭角色无关）：普通用户 / 平台运营（维护公共菜谱库、字典） / 超级管理员。 */
public enum PlatformRole {
    USER, OPERATOR, SUPER_ADMIN;

    public boolean atLeast(PlatformRole other) {
        return ordinal() >= other.ordinal();
    }

    public static PlatformRole of(String s) {
        if (s == null || s.isBlank()) return USER;
        try {
            return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
