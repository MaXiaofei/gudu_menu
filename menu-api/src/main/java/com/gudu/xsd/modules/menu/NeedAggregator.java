package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.dish.DishIngredient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量聚合（纯函数）：把食集里各菜的食材用量，按 ingredientId 汇总成用量原文明细。
 *
 * <p>算法：① 按 dishId 合并 servingFactor（同菜多行=多份，audit §6 数据层正确聚合）
 *      ② 按 ingredientId 分组，保留每笔用量原文（amount + unitName）
 * V55（食材去单位）：不再按克汇总，聚合只回答「这次用到哪些食材、每道菜用多少」。
 * 不碰任何 Mapper，便于单测。调用方负责查数据传入。
 */
@Component
public class NeedAggregator {

    /** 一笔用量原文：菜 id + 数字 + 单位名 + 份数（>1 时展示 ×N）。 */
    public record UsageText(Long dishId, BigDecimal amount, String unitName, BigDecimal servingFactor) {}

    public Map<Long, List<UsageText>> aggregate(List<MenuDish> menuDishes,
                                                Map<Long, List<DishIngredient>> dishIngredientsByDish) {
        Map<Long, List<UsageText>> needByIng = new HashMap<>();
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
        // 2) 按食材分组用量原文（用量缺失跳过该行）
        for (Map.Entry<Long, BigDecimal> e : factorByDish.entrySet()) {
            List<DishIngredient> ings = dishIngredientsByDish.get(e.getKey());
            if (ings == null) continue;
            for (DishIngredient di : ings) {
                if (di == null || di.getIngredientId() == null || di.getAmount() == null) continue;
                needByIng.computeIfAbsent(di.getIngredientId(), k -> new ArrayList<>())
                        .add(new UsageText(di.getDishId(), di.getAmount(), di.getUnitName(), e.getValue()));
            }
        }
        return needByIng;
    }
}
