package com.gudu.xsd.modules.vision.domain.service;

import com.gudu.xsd.modules.vision.domain.model.ConfidenceLevel;

import java.util.Optional;

/** 置信度分级：≥0.8 AUTO（默认勾选）、0.5–0.8 CONFIRM（标黄待确认）、&lt;0.5 不展示。 */
public final class ConfidencePolicy {

    public static final double AUTO_MIN = 0.8;
    public static final double CONFIRM_MIN = 0.5;

    private ConfidencePolicy() {
    }

    public static Optional<ConfidenceLevel> classify(double confidence) {
        double c = normalize(confidence);
        if (c >= AUTO_MIN) return Optional.of(ConfidenceLevel.AUTO);
        if (c >= CONFIRM_MIN) return Optional.of(ConfidenceLevel.CONFIRM);
        return Optional.empty();
    }

    /** 归一化到 0..1：百分制（1..100）除以 100；NaN/负数记 0；超过 100 记 1。 */
    public static double normalize(double confidence) {
        if (Double.isNaN(confidence) || confidence <= 0) return 0;
        if (confidence <= 1) return confidence;
        if (confidence <= 100) return confidence / 100.0;
        return 1;
    }
}
