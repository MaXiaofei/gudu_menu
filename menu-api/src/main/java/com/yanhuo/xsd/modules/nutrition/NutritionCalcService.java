package com.yanhuo.xsd.modules.nutrition;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营养聚合纯函数：单道菜营养 = Σ(食材营养 per100g × 用量g / 100)，可整体按份数缩放。
 * 这是整个系统的算法地基，不依赖任何外部状态。
 */
@Service
public class NutritionCalcService {

    /** 一条「食材-指标」贡献项：该食材该指标 per 100g 的值 + 用量克数。 */
    public record Item(Long metricId, BigDecimal valuePer100g, BigDecimal grams) {}

    /** 聚合单道菜营养（1 份）。 */
    public Map<Long, BigDecimal> aggregateDish(List<Item> dish) {
        return aggregateDish(dish, BigDecimal.ONE);
    }

    /** 聚合单道菜营养，并整体按份数缩放。 */
    public Map<Long, BigDecimal> aggregateDish(List<Item> dish, BigDecimal servingFactor) {
        Map<Long, BigDecimal> sum = new HashMap<>();
        for (Item it : dish) {
            BigDecimal contrib = it.valuePer100g()
                    .multiply(it.grams())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            sum.merge(it.metricId(), contrib, BigDecimal::add);
        }
        if (servingFactor.compareTo(BigDecimal.ONE) != 0) {
            sum.replaceAll((k, v) -> v.multiply(servingFactor));
        }
        return sum;
    }
}
