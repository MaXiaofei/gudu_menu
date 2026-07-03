package com.gudu.xsd.modules.shopping;

import java.math.BigDecimal;

/**
 * 采购余色判定（纯函数，算法地基）。
 *
 * <p>比对采购参考量 {@code needGrams}（shopping_item.reference_grams）vs 家中现有
 * {@code pantryGrams}（pantry 按 ingredient_id 聚合的 grams），产出三色：
 * <ul>
 *   <li>🔴 {@link Status#RED_NONE} 没有 —— pantry 无该食材或 grams=0；</li>
 *   <li>🟡 {@link Status#YELLOW_SHORT} 差 X —— pantry 有但不够（&lt; need）；</li>
 *   <li>🟢 {@link Status#GREEN_ENOUGH} 够 —— pantry &gt;= need。</li>
 * </ul>
 *
 * <p>不标记（返回 {@code null}，前端标灰「手动加」）：needGrams 为 null 或 &lt;= 0
 * （customName 手动加项无参考量、或 referenceGrams 未配置）。
 *
 * <p>不依赖任何 Mapper / Spring 状态，可单测。参照 {@link ShoppingAggregator}、
 * {@code NeedAggregator} 纯函数范式。
 */
public class StockClassifier {

    /** 三色状态（枚举铁律：DB/JSON 存大写名，前端映射中文+色）。 */
    public enum Status { RED_NONE, YELLOW_SHORT, GREEN_ENOUGH }

    /** 判定结果：状态 + 差量（RED 时=needGrams，YELLOW 时=need-have，GREEN 时=0）。 */
    public record Result(Status status, BigDecimal shortageGrams) {}

    /**
     * 比对采购需求 vs 家中现有，返回三色结果。
     *
     * @param needGrams   采购参考量（shopping_item.reference_grams），可空
     * @param pantryGrams 家中现有克数（pantry 按 ingredient_id 聚合），可空
     * @return 三色结果；needGrams 无效（null / &lt;= 0）时返回 null（不标记）
     */
    public Result classify(BigDecimal needGrams, BigDecimal pantryGrams) {
        if (needGrams == null || needGrams.signum() <= 0) return null;
        BigDecimal have = pantryGrams == null ? BigDecimal.ZERO : pantryGrams;
        if (have.signum() <= 0) {
            return new Result(Status.RED_NONE, needGrams);
        }
        if (have.compareTo(needGrams) < 0) {
            return new Result(Status.YELLOW_SHORT, needGrams.subtract(have));
        }
        return new Result(Status.GREEN_ENOUGH, BigDecimal.ZERO);
    }
}
