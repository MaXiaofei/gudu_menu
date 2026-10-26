package com.gudu.xsd.modules.consent.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.util.Locale;

/** 同意的文件类型：用户协议、隐私政策；健康档案 / 不满 14 周岁成员单独同意（B.2 使用）。 */
public enum ConsentDocType {
    TERMS, PRIVACY, HEALTH, MINOR;

    public static ConsentDocType of(String s) {
        if (s == null || s.isBlank()) throw new DomainException("缺少协议类型");
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("不支持的协议类型：" + s);
        }
    }
}
