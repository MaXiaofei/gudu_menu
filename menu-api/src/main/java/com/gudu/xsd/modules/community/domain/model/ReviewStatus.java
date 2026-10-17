package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/**
 * 审核队列视图：PENDING 待处理（未复核）；DONE 已人工处置；APPEALED 作者申诉待处理。
 */
public enum ReviewStatus {
    PENDING, DONE, APPEALED;

    public static ReviewStatus of(String s) {
        if (s == null || s.isBlank()) return PENDING;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new DomainException("status 只能是 PENDING / DONE / APPEALED");
        }
    }
}
