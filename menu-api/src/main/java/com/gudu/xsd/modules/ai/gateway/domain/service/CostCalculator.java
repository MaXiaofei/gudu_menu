package com.gudu.xsd.modules.ai.gateway.domain.service;

import com.gudu.xsd.modules.ai.gateway.domain.model.ModelPrice;
import com.gudu.xsd.modules.ai.gateway.domain.model.PeakHours;
import com.gudu.xsd.modules.ai.gateway.domain.model.TokenUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * 成本计算（纯函数）：单价为「分 / 百万 token」高峰价；按调用时刻判断高峰，非高峰乘系数；
 * 输入拆成缓存命中与未命中分别计价。结果单位：分，保留 4 位小数。
 */
public final class CostCalculator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    private CostCalculator() {
    }

    public static BigDecimal costFen(ModelPrice price, PeakHours peak, TokenUsage usage, Instant at) {
        if (price == null || usage == null) return BigDecimal.ZERO;
        BigDecimal raw = price.inputCacheHitFen().multiply(BigDecimal.valueOf(usage.cacheHitTokens()))
                .add(price.inputFen().multiply(BigDecimal.valueOf(usage.cacheMissTokens())))
                .add(price.outputFen().multiply(BigDecimal.valueOf(usage.completionTokens())));
        BigDecimal ratio = peak == null ? BigDecimal.ONE : peak.ratioAt(at);
        return raw.multiply(ratio).divide(MILLION, 4, RoundingMode.HALF_UP);
    }
}
