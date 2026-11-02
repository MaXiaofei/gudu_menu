package com.gudu.xsd.modules.aiguard.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 今日花费状态。limitFen ≤ 0 表示不限（永远 NORMAL）。
 *
 * @param spentFen 今日已花费（分）
 * @param limitFen 今日上限（分）
 * @param percent  已用百分比（向下取整；不限时为 0）
 */
public record BudgetStatus(BigDecimal spentFen, BigDecimal limitFen, int percent, Level level) {

    public enum Level {
        /** 正常。 */
        NORMAL,
        /** 已达提醒比例（默认 80%）。 */
        WARN,
        /** 已达上限，暂停调用模型。 */
        PAUSED
    }

    public static BudgetStatus of(BigDecimal spentFen, BigDecimal limitFen, int warnPercent) {
        if (limitFen == null || limitFen.signum() <= 0) {
            return new BudgetStatus(spentFen, BigDecimal.ZERO, 0, Level.NORMAL);
        }
        int percent = spentFen.multiply(BigDecimal.valueOf(100)).divide(limitFen, 0, RoundingMode.DOWN).intValue();
        Level level = spentFen.compareTo(limitFen) >= 0 ? Level.PAUSED
                : percent >= warnPercent ? Level.WARN : Level.NORMAL;
        return new BudgetStatus(spentFen, limitFen, percent, level);
    }

    public boolean exhausted() {
        return level == Level.PAUSED;
    }
}
