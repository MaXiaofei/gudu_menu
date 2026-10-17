package com.gudu.xsd.modules.mealplan.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.util.List;

/**
 * 生成餐程的约束输入。
 *
 * @param weekdayMaxMinutes 工作日单道菜时长上限（可空）
 * @param budgetFen         一周采购预算（分，可空）
 */
public record PlanRequest(WeekRange week, List<Meal> meals, int servings, List<Long> memberIds,
                          Integer weekdayMaxMinutes, Integer budgetFen, boolean preferExpiring) {

    public static final int MAX_SERVINGS = 20;

    public PlanRequest {
        if (week == null) throw new DomainException("缺少周范围");
        meals = meals == null || meals.isEmpty() ? List.of(Meal.LUNCH, Meal.DINNER)
                : meals.stream().distinct().sorted().toList();
        if (servings < 1 || servings > MAX_SERVINGS) throw new DomainException("人份需在 1–20 之间");
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        if (weekdayMaxMinutes != null && weekdayMaxMinutes < 5) throw new DomainException("工作日时长上限至少 5 分钟");
        if (budgetFen != null && budgetFen < 0) throw new DomainException("预算不能为负");
    }

    public int slotCount() {
        return WeekRange.DAYS * meals.size();
    }
}
