package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 运营处置动作。 */
public enum ModerationAction {
    APPROVE, HIDE, BAN_7D;

    public static ModerationAction of(String s) {
        try {
            return valueOf(s);
        } catch (RuntimeException e) {
            throw new DomainException("处置动作不合法");
        }
    }
}
