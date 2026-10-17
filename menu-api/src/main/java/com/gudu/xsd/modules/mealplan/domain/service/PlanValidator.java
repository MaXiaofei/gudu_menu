package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.SlotBadge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 餐程校验：每天人均钠（午晚菜 1 人份 + 早餐等其它摄入基线）不超过档案上限；本周不重样；
 * 有宝宝时每天至少一格宝宝可吃；每格都排上了菜。
 */
public final class PlanValidator {

    /** 早餐与零食等非排餐摄入的钠基线（mg/人/天）。 */
    public static final double BASELINE_SODIUM_MG = 300;

    /**
     * @param offendingDishIds 导致问题的菜（回炉时排除）
     */
    public record DayIssue(LocalDate date, String reason, Set<Long> offendingDishIds) {
    }

    private PlanValidator() {
    }

    public static List<DayIssue> validate(List<PlannedSlot> slots, Map<Long, DishCandidate> byId,
                                          HouseholdProfile profile, List<LocalDate> days, int mealsPerDay) {
        List<DayIssue> issues = new ArrayList<>();
        Map<LocalDate, List<PlannedSlot>> byDay = groupByDay(slots);
        Set<Long> seen = new HashSet<>();
        for (LocalDate day : days) {
            List<PlannedSlot> ds = byDay.getOrDefault(day, List.of());
            if (ds.size() < mealsPerDay) issues.add(new DayIssue(day, "有一餐没排上菜", Set.of()));
            double sodium = dailySodium(ds, byId);
            if (sodium > profile.sodiumLimitMg()) {
                issues.add(new DayIssue(day, String.format("钠 %.1fg 超出 %.1fg", sodium / 1000, profile.sodiumLimitMg() / 1000.0),
                        Set.of(saltiest(ds, byId))));
            }
            for (PlannedSlot s : ds) {
                if (!seen.add(s.dishId())) issues.add(new DayIssue(day, "菜重复了", Set.of(s.dishId())));
            }
            if (profile.hasBaby() && ds.stream().noneMatch(s -> s.badges().contains(SlotBadge.BABY))) {
                issues.add(new DayIssue(day, "缺一道宝宝可吃的菜", Set.of()));
            }
        }
        return issues;
    }

    /** 人均日钠（mg）。 */
    public static double dailySodium(List<PlannedSlot> daySlots, Map<Long, DishCandidate> byId) {
        double s = BASELINE_SODIUM_MG;
        for (PlannedSlot p : daySlots) {
            DishCandidate c = byId.get(p.dishId());
            if (c != null) s += c.perServing().sodiumMg();
        }
        return s;
    }

    public static Map<LocalDate, List<PlannedSlot>> groupByDay(List<PlannedSlot> slots) {
        Map<LocalDate, List<PlannedSlot>> out = new LinkedHashMap<>();
        for (PlannedSlot s : slots) out.computeIfAbsent(s.date(), d -> new ArrayList<>()).add(s);
        return out;
    }

    private static Long saltiest(List<PlannedSlot> ds, Map<Long, DishCandidate> byId) {
        PlannedSlot best = null;
        double max = -1;
        for (PlannedSlot p : ds) {
            DishCandidate c = byId.get(p.dishId());
            double v = c == null ? 0 : c.perServing().sodiumMg();
            if (v > max) {
                max = v;
                best = p;
            }
        }
        return best == null ? -1L : best.dishId();
    }
}
