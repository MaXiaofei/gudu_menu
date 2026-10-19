package com.gudu.xsd.modules.mealplan.domain.model;

import java.util.List;
import java.util.Set;

/**
 * 排餐候选菜（已估算 1 人份营养与成本）。
 *
 * @param minutes      准备 + 烹饪（分钟，未知为 null）
 * @param perServing   1 人份营养估算
 * @param costFen      整道菜食材成本估算（分，不含调料）
 * @param babyFriendly 宝宝可吃（软烂少盐不辣）
 */
public record DishCandidate(long dishId, String name, Integer minutes, Set<String> tags, Set<Long> ingredientIds,
                            List<String> ingredientNames, DishKind kind, Nutrients perServing, int costFen,
                            boolean babyFriendly) {

    public DishCandidate {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        ingredientIds = ingredientIds == null ? Set.of() : Set.copyOf(ingredientIds);
        ingredientNames = ingredientNames == null ? List.of() : List.copyOf(ingredientNames);
        perServing = perServing == null ? Nutrients.ZERO : perServing;
    }
}
