package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 互动类型：赞 / 收藏。 */
public enum ReactionType {
    LIKE, COLLECT;

    public static ReactionType of(String s) {
        try {
            return valueOf(s);
        } catch (RuntimeException e) {
            throw new DomainException("互动类型不合法");
        }
    }

    public PostCounter counter() {
        return this == LIKE ? PostCounter.LIKE : PostCounter.COLLECT;
    }
}
