package com.gudu.xsd.modules.aiguard.domain.model;

import com.gudu.xsd.common.security.AiQuotaCategory;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * AI 额度配置：全站每日花费上限（元，0 = 不限）、提醒比例（%）、各类每账号每日次数上限（0 = 不限）。
 */
public record AiGuardConfig(BigDecimal dailyBudgetYuan, int warnPercent, Map<AiQuotaCategory, Integer> limits) {

    public static final String KEY_BUDGET = "budget.daily_yuan";
    public static final String KEY_WARN = "budget.warn_percent";
    public static final String KEY_LIMIT_PREFIX = "limit.";

    public AiGuardConfig {
        Map<AiQuotaCategory, Integer> copy = new EnumMap<>(AiQuotaCategory.class);
        copy.putAll(limits);
        limits = Map.copyOf(copy);
    }

    /** 某类每账号每日次数上限（≤0 = 不限）。 */
    public int limit(AiQuotaCategory category) {
        return limits.getOrDefault(category, 0);
    }

    /** 全站每日花费上限，单位分（≤0 = 不限）。 */
    public BigDecimal budgetFen() {
        return dailyBudgetYuan.multiply(BigDecimal.valueOf(100));
    }

    /** 存储键：limit.vision 等。 */
    public static String limitKey(AiQuotaCategory category) {
        return KEY_LIMIT_PREFIX + category.name().toLowerCase();
    }
}
