package com.gudu.xsd.modules.feedback.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 反馈处理状态：NEW 待处理 / REPLIED 已回复 / DONE 已处理。 */
public enum FeedbackStatus {
    NEW, REPLIED, DONE;

    public static FeedbackStatus of(String value) {
        if (value != null) {
            for (FeedbackStatus s : values()) {
                if (s.name().equals(value)) return s;
            }
        }
        throw new DomainException("反馈状态不合法");
    }
}
