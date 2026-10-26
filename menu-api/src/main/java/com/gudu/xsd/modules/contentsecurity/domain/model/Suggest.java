package com.gudu.xsd.modules.contentsecurity.domain.model;

import java.util.Locale;

/** 检测建议（对齐微信 v2 result.suggest）：pass 通过 / review 疑似 / risky 违规。 */
public enum Suggest {
    PASS,
    REVIEW,
    RISKY;

    /** 微信返回的 suggest 字符串；无法识别按 REVIEW（保守，不当作通过）。 */
    public static Suggest ofWx(String s) {
        if (s == null) return REVIEW;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "pass" -> PASS;
            case "risky" -> RISKY;
            default -> REVIEW;
        };
    }

    /** 该建议在给定用途下是否拦截。 */
    public boolean blocks(ContentPurpose purpose) {
        return this == RISKY || (this == REVIEW && purpose.blocksReview());
    }
}
