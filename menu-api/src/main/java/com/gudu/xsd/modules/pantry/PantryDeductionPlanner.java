package com.gudu.xsd.modules.pantry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * FIFO 扣减规划（纯函数）：给定已排好序的 pantry 批次和需求克数，
 * 算出每个批次扣多少、最终欠多少。不碰 DB，便于单测。
 *
 * 调用方须保证 batches 已按"过期日升序（null 最后）、id 升序"排好（FIFO 先扣早过期的）。
 * amount（按 unitId 的个数）随 grams 按比例缩放，保证两字段一致。
 */
public class PantryDeductionPlanner {

    public DeductPlan plan(List<Pantry> batchesSorted, BigDecimal needGrams) {
        if (needGrams == null || needGrams.signum() <= 0) {
            return new DeductPlan(List.of(), BigDecimal.ZERO);
        }
        BigDecimal remaining = needGrams;
        List<BatchDeduction> ops = new ArrayList<>();
        if (batchesSorted != null) {
            for (Pantry p : batchesSorted) {
                if (p == null || p.getId() == null) continue;
                if (remaining.signum() <= 0) break;
                BigDecimal avail = p.getGrams() == null ? BigDecimal.ZERO : p.getGrams();
                if (avail.signum() <= 0) continue;
                BigDecimal take = avail.min(remaining);
                BigDecimal remainAfter = avail.subtract(take);
                BigDecimal newAmount = scaleAmount(p.getAmount(), avail, take);
                ops.add(new BatchDeduction(p.getId(), take, remainAfter, newAmount));
                remaining = remaining.subtract(take);
            }
        }
        return new DeductPlan(ops, remaining.signum() > 0 ? remaining : BigDecimal.ZERO);
    }

    /** amount 按所扣比例缩放：newAmount = amount × (1 - take/avail)。avail=0 时原值不动。 */
    private BigDecimal scaleAmount(BigDecimal amount, BigDecimal avail, BigDecimal take) {
        if (amount == null) return null;
        if (avail.signum() <= 0) return amount;
        BigDecimal remainRatio = BigDecimal.ONE.subtract(take.divide(avail, 6, RoundingMode.HALF_UP));
        return amount.multiply(remainRatio).setScale(2, RoundingMode.HALF_UP);
    }

    /** 单批次扣减结果。 */
    public record BatchDeduction(Long pantryId, BigDecimal deductGrams,
                                 BigDecimal remainGrams, BigDecimal newAmount) {}

    /** 整体扣减规划：对各批次的操作 + 最终欠量（pantry 不记负，扣不动的进 shortage）。 */
    public record DeductPlan(List<BatchDeduction> ops, BigDecimal shortageGrams) {}
}
