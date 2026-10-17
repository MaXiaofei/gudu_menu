package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.PlanCheck;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 排餐编排：规则过滤 → 排布 → 校验 → 未通过的天回炉（排除导致超标/重复的菜，其它天不动），最多回炉 2 轮。
 * 仍不通过的天在结论里标注提醒，不阻止出草稿。
 */
public final class MealPlanner {

    public static final int MAX_REWORK_ROUNDS = 2;

    public record Outcome(List<PlannedSlot> slots, PlanCheck check) {
    }

    private final MealArranger arranger;

    public MealPlanner(MealArranger arranger) {
        this.arranger = arranger;
    }

    public Outcome plan(PlanRequest req, HouseholdProfile profile, List<DishCandidate> candidates,
                        Set<Long> expiringIngredientIds) {
        List<DishCandidate> pool = candidates.stream().filter(c -> CandidateFilter.allowed(c, profile)).toList();
        Map<Long, DishCandidate> byId = pool.stream()
                .collect(Collectors.toMap(DishCandidate::dishId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<LocalDate> days = req.week().days();
        int mealsPerDay = req.meals().size();

        List<PlannedSlot> slots = arranger.arrange(new ArrangeContext(req, profile, pool, expiringIngredientIds,
                days, List.of(), Set.of()));
        List<PlanValidator.DayIssue> issues = PlanValidator.validate(slots, byId, profile, days, mealsPerDay);
        int rounds = 1;
        Set<Long> excluded = new HashSet<>();
        while (!issues.isEmpty() && rounds <= MAX_REWORK_ROUNDS) {
            Set<LocalDate> badDays = issues.stream().map(PlanValidator.DayIssue::date)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            issues.forEach(i -> excluded.addAll(i.offendingDishIds()));
            List<PlannedSlot> fixed = slots.stream().filter(s -> !badDays.contains(s.date())).toList();
            List<PlannedSlot> redo = arranger.arrange(new ArrangeContext(req, profile, pool, expiringIngredientIds,
                    days.stream().filter(badDays::contains).toList(), fixed, excluded));
            List<PlannedSlot> merged = new ArrayList<>(fixed);
            merged.addAll(redo);
            merged.sort(Comparator.comparing(PlannedSlot::date).thenComparing(PlannedSlot::meal));
            slots = merged;
            rounds++;
            issues = PlanValidator.validate(slots, byId, profile, days, mealsPerDay);
        }
        return new Outcome(slots, summarize(slots, byId, profile, issues, rounds));
    }

    static PlanCheck summarize(List<PlannedSlot> slots, Map<Long, DishCandidate> byId, HouseholdProfile profile,
                               List<PlanValidator.DayIssue> issues, int rounds) {
        List<String> notes = new ArrayList<>();
        Map<LocalDate, List<PlannedSlot>> byDay = PlanValidator.groupByDay(slots);
        double avg = byDay.values().stream().mapToDouble(d -> PlanValidator.dailySodium(d, byId)).average().orElse(0);
        double limitG = profile.sodiumLimitMg() / 1000.0;
        String limit = limitG == Math.rint(limitG) ? String.valueOf((long) limitG) : String.format("%.1f", limitG);
        notes.add(String.format("钠日均 %.1fg（≤%sg）", avg / 1000, limit));
        long distinct = slots.stream().map(PlannedSlot::dishId).distinct().count();
        notes.add(distinct == slots.size() ? distinct + " 道不重样" : distinct + " 道菜，有重复");
        for (PlanValidator.DayIssue i : issues) notes.add(WeekRange.weekdayLabel(i.date()) + " " + i.reason());
        return new PlanCheck(issues.isEmpty(), rounds, notes);
    }

    /** 换一道时重算结论（不回炉）。 */
    public static PlanCheck recheck(List<PlannedSlot> slots, List<DishCandidate> candidates, HouseholdProfile profile,
                                    PlanRequest req, int rounds) {
        Map<Long, DishCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(DishCandidate::dishId, Function.identity(), (a, b) -> a));
        List<PlanValidator.DayIssue> issues = PlanValidator.validate(slots, byId, profile, req.week().days(),
                req.meals().size());
        return summarize(slots, byId, profile, issues, rounds);
    }
}
