package com.gudu.xsd.modules.ai;

import com.gudu.xsd.common.BizException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * AI 输出校验护栏：对外部 AI 返回的营养值做合理性校验，防离谱值（如 calorie=9999999）。
 *
 * <p>策略：每个指标有 {@code hardMax}（clamp 上限）和 {@code rejectThreshold}（明显离谱拒绝阈值，
 * 约为 hardMax 的 10 倍）。负数一律 clamp 到 0；超 hardMax 但未达 rejectThreshold 的值 clamp 到 hardMax；
 * 超 rejectThreshold 抛 {@link BizException}（AI 返回值异常，调用方降级 mock）。
 *
 * <p>两种模式：{@code per100g=true}（营养补全，每100g）与 {@code per100g=false}（整餐/一餐总量）。
 * metricId：1cal / 2protein / 3fat / 4carb / 5sugar / 6gi。
 */
@Component
public class AiOutputGuard {

    // per100g 范围
    private static final BigDecimal PER_CAL_MAX = new BigDecimal("3000");
    private static final BigDecimal PER_MACRO_MAX = new BigDecimal("500");   // protein/fat/carb/sugar
    private static final BigDecimal GI_MAX = new BigDecimal("100");
    // 整餐 范围
    private static final BigDecimal MEAL_CAL_MAX = new BigDecimal("20000");
    private static final BigDecimal MEAL_MACRO_MAX = new BigDecimal("2000");
    // 拒绝阈值（约 hardMax×10）
    private static final BigDecimal REJECT_FACTOR = new BigDecimal("10");

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 校验营养值，返回 clamp 后的 map（原 map 不变）。
     *
     * @param nutrition metricId → value
     * @param per100g   true=每100g（营养补全），false=整餐总量（菜品估算）
     */
    public Map<Long, BigDecimal> validateNutrition(Map<Long, BigDecimal> nutrition, boolean per100g) {
        if (nutrition == null || nutrition.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> e : nutrition.entrySet()) {
            out.put(e.getKey(), clamp(e.getKey(), e.getValue(), per100g));
        }
        return out;
    }

    private BigDecimal clamp(Long metricId, BigDecimal v, boolean per100g) {
        if (v == null) v = ZERO;
        // 负数 clamp 到 0
        if (v.signum() < 0) return ZERO;
        BigDecimal hardMax;
        if (per100g) {
            hardMax = (metricId == 6L) ? GI_MAX
                    : (metricId == 1L) ? PER_CAL_MAX : PER_MACRO_MAX;
        } else {
            // 整餐无 gi（gi 不适用整体餐），仍按 macro 上限处理
            hardMax = (metricId == 1L) ? MEAL_CAL_MAX : MEAL_MACRO_MAX;
        }
        BigDecimal reject = hardMax.multiply(REJECT_FACTOR);
        if (v.compareTo(reject) > 0) {
            throw new BizException("AI 返回值异常（" + metricId + "=" + v + "）");
        }
        if (v.compareTo(hardMax) > 0) {
            return hardMax;
        }
        return v;
    }
}
