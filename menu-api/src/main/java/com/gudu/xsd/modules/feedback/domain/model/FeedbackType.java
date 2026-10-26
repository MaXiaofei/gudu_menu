package com.gudu.xsd.modules.feedback.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.util.Locale;

/** 反馈类型：general 一般意见 / ai AI 生成内容反馈 / complaint 投诉举报。库里存小写 code。 */
public enum FeedbackType {
    GENERAL("general"), AI("ai"), COMPLAINT("complaint");

    private final String code;

    FeedbackType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 提交时：空 → general；未知值报错。 */
    public static FeedbackType ofOrGeneral(String value) {
        if (value == null || value.isBlank()) return GENERAL;
        return of(value);
    }

    public static FeedbackType of(String value) {
        if (value != null) {
            String v = value.trim().toLowerCase(Locale.ROOT);
            for (FeedbackType t : values()) {
                if (t.code.equals(v)) return t;
            }
        }
        throw new DomainException("反馈类型不合法");
    }
}
