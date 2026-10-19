package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.math.BigDecimal;

/**
 * 模型单价（高峰价，单位：分 / 百万 token）。
 *
 * @param inputCacheHitFen 输入命中缓存
 * @param inputFen         输入未命中缓存
 * @param outputFen        输出
 */
public record ModelPrice(BigDecimal inputCacheHitFen, BigDecimal inputFen, BigDecimal outputFen) {

    public static final ModelPrice FREE = new ModelPrice(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public ModelPrice {
        inputCacheHitFen = nz(inputCacheHitFen);
        inputFen = nz(inputFen);
        outputFen = nz(outputFen);
        if (inputCacheHitFen.signum() < 0 || inputFen.signum() < 0 || outputFen.signum() < 0) {
            throw new IllegalArgumentException("单价不能为负");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
