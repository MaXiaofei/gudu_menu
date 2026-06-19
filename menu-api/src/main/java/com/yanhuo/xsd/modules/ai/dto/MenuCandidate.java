package com.yanhuo.xsd.modules.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 一组推荐菜单候选。
 *
 * @param dishes           本组菜品列表（dishId/名称/份数/单价）
 * @param totalPrice       总价 = Σ(price × servingFactor)
 * @param totalNutrition   总营养 = 各菜营养按份数累加（metricId -> 值）
 * @param score            打分（越高越优，蛋白占比与达标度综合）
 * @param reasons          推荐理由（中文，给人看的解释）
 * @param source           mock / glm
 */
public record MenuCandidate(
        List<DishItem> dishes,
        BigDecimal totalPrice,
        Map<Long, BigDecimal> totalNutrition,
        double score,
        List<String> reasons,
        String source) {

    /** 候选组里的一道菜。 */
    public record DishItem(Long dishId, String name, BigDecimal servingFactor, BigDecimal price) {
    }
}
