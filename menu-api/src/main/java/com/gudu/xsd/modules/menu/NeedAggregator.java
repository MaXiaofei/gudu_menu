package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.dish.DishIngredient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量聚合（纯函数）：把食集里各菜的食材用量，按 ingredientId 汇总成"共需多少克"。
 *
 * 算法：① 按 dishId 合并 servingFactor（同菜多行=多份，audit §6 数据层正确聚合）
 *      ② Σ(grams × factor) by ingredientId
 * 不碰任何 Mapper，便于单测。调用方负责查数据传入。
 */
@Component
public class NeedAggregator {

    public Map<Long, BigDecimal> aggregate(List<MenuDish> menuDishes,
                                            Map<Long, List<DishIngredient>> dishIngredientsByDish) {
        Map<Long, BigDecimal> needByIng = new HashMap<>();
        if (menuDishes == null || menuDishes.isEmpty() || dishIngredientsByDish == null) {
            return needByIng;
        }
        // 1) 按 dishId 合并份数（同菜多行累加）
        Map<Long, BigDecimal> factorByDish = new HashMap<>();
        for (MenuDish md : menuDishes) {
            if (md == null || md.getDishId() == null) continue;
            BigDecimal f = md.getServingFactor() == null ? BigDecimal.ONE : md.getServingFactor();
            factorByDish.merge(md.getDishId(), f, BigDecimal::add);
        }
        // 2) 按食材聚合
        for (Map.Entry<Long, BigDecimal> e : factorByDish.entrySet()) {
            List<DishIngredient> ings = dishIngredientsByDish.get(e.getKey());
            if (ings == null) continue;
            for (DishIngredient di : ings) {
                if (di == null || di.getIngredientId() == null || di.getGrams() == null) continue;
                BigDecimal need = di.getGrams().multiply(e.getValue());
                needByIng.merge(di.getIngredientId(), need, BigDecimal::add);
            }
        }
        return needByIng;
    }
}
