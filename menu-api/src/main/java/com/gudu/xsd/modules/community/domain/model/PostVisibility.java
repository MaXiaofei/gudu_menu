package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 可见范围：所有人 / 仅关注我的人。 */
public enum PostVisibility {
    PUBLIC, FOLLOWERS;

    public static PostVisibility of(String s) {
        if (s == null || s.isBlank()) return PUBLIC;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new DomainException("可见范围不合法");
        }
    }
}
