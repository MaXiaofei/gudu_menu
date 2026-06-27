package com.gudu.xsd.modules.dailylog;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日饮食记录的营养聚合纯函数：把一组摄入项(菜品/食材)聚合成总营养(metricId→value)。
 *
 * 算法（复用 NutritionCalcService 思想，独立成纯函数便于可测，不注入任何 Mapper）：
 *  - ingredient 项：valuePerUnit 是该指标 per100g，qty 是克 → value × qty / 100
 *  - dish 项：valuePerUnit 是该菜 per份营养(已聚合)，qty 是份数 → value × qty
 *
 * 这是整个 dailylog 模块的算法地基，不依赖任何外部状态(参照 NutritionCalcService / MealPlanService.detectDuplicates)。
 */
@Service
public class DailyLogCalcService {

    /**
     * 一条「指标-摄入」贡献项。
     *
     * @param metricId     营养指标 id
     * @param isDish       true=菜品摄入(valuePerUnit 是 per份、qty 是份数)；false=食材摄入(valuePerUnit 是 per100g、qty 是克)
     * @param valuePerUnit 单位营养值(菜品=per份、食材=per100g)
     * @param qty          数量(菜品=份数、食材=克)
     */
    public record Intake(Long metricId, boolean isDish, BigDecimal valuePerUnit, BigDecimal qty) {}

    /**
     * 聚合各摄入项的总营养。ingredient 项 value×qty/100，dish 项 value×qty，按 metricId 累加。
     * 空列表返回空 map。null 值安全跳过。
     */
    public Map<Long, BigDecimal> aggregateIntake(List<Intake> items) {
        Map<Long, BigDecimal> sum = new HashMap<>();
        if (items == null || items.isEmpty()) return sum;
        for (Intake it : items) {
            if (it == null || it.valuePerUnit() == null || it.qty() == null) continue;
            BigDecimal contrib = it.isDish()
                    ? it.valuePerUnit().multiply(it.qty())
                    : it.valuePerUnit().multiply(it.qty())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            sum.merge(it.metricId(), contrib, BigDecimal::add);
        }
        return sum;
    }
}
