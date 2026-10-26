package com.gudu.xsd.modules.family.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 家庭名：去首尾空白后 1–20 字。 */
public record FamilyName(String value) {

    static final int MAX = 20;

    public FamilyName {
        if (value == null || value.isBlank() || value.trim().length() > MAX) {
            throw new DomainException("家庭名需 1–20 个字");
        }
        value = value.trim();
    }
}
