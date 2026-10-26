package com.gudu.xsd.modules.contentsecurity.domain.model;

/** 违规图片人工复核结论（F.3）：确认违规 / 误判已恢复。 */
public enum MediaReviewStatus {
    CONFIRMED, RESTORED;

    public static MediaReviewStatus of(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
