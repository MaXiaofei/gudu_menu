package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 排布输入。
 *
 * @param pool                已过滤的候选（{@link CandidateFilter#allowed}）
 * @param expiringIngredientIds 快到期食材（优先用掉）
 * @param days                本次要排的日期（回炉时只排未通过的天）
 * @param fixed               不动的格（其它天的结果，参与去重与预算）
 * @param excludedDishIds     本轮禁用的菜（上一轮导致超标的）
 */
public record ArrangeContext(PlanRequest request, HouseholdProfile profile, List<DishCandidate> pool,
                             Set<Long> expiringIngredientIds, List<LocalDate> days, List<PlannedSlot> fixed,
                             Set<Long> excludedDishIds) {

    public ArrangeContext {
        pool = pool == null ? List.of() : List.copyOf(pool);
        expiringIngredientIds = expiringIngredientIds == null ? Set.of() : Set.copyOf(expiringIngredientIds);
        days = days == null ? request.week().days() : List.copyOf(days);
        fixed = fixed == null ? List.of() : List.copyOf(fixed);
        excludedDishIds = excludedDishIds == null ? Set.of() : Set.copyOf(excludedDishIds);
    }
}
