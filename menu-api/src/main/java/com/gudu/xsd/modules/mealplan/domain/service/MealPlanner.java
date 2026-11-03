package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.PlanCheck;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;

import java.time.Clock;
import java.time.Duration;
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

    /** 回炉轮次里，从请求开始已耗时超过它就不再调大模型（改用廉价排布），控制整次请求总耗时。 */
    public static final Duration DEFAULT_LLM_REWORK_BUDGET = Duration.ofSeconds(20);

    public static final String RELAXED_NOTE_PREFIX = "工作日 ≤";
    public static final String RELAXED_NOTE_SUFFIX = " 分钟内能做的菜不够，工作日已不限时长";

    /** @param request 实际生效的请求（时长上限被放宽时 weekdayMaxMinutes 为 null），草稿应以它落库 */
    public record Outcome(List<PlannedSlot> slots, PlanCheck check, PlanRequest request) {
    }

    private final MealArranger arranger;
    private final MealArranger cheapArranger;
    private final Clock clock;
    private final Duration llmReworkBudget;

    public MealPlanner(MealArranger arranger) {
        this(arranger, arranger, Clock.systemUTC(), DEFAULT_LLM_REWORK_BUDGET);
    }

    /**
     * @param cheapArranger  不调模型的排布（启发式）；回炉超时或候选不足时用它
     * @param llmReworkBudget 首轮之后允许继续调模型回炉的最长已耗时
     */
    public MealPlanner(MealArranger arranger, MealArranger cheapArranger, Clock clock, Duration llmReworkBudget) {
        this.arranger = arranger;
        this.cheapArranger = cheapArranger;
        this.clock = clock;
        this.llmReworkBudget = llmReworkBudget;
    }

    public Outcome plan(PlanRequest req, HouseholdProfile profile, List<DishCandidate> candidates,
                        Set<Long> expiringIngredientIds) {
        return plan(req, profile, candidates, expiringIngredientIds, null);
    }

    public Outcome plan(PlanRequest origReq, HouseholdProfile profile, List<DishCandidate> candidates,
                        Set<Long> expiringIngredientIds, Long operatorId) {
        long startMs = clock.millis();
        List<DishCandidate> pool = candidates.stream().filter(c -> CandidateFilter.allowed(c, profile)).toList();
        PlanRequest req = origReq;
        String relaxedNote = null;
        if (origReq.weekdayMaxMinutes() != null && !enoughWeekdayDishes(origReq, pool)) {
            relaxedNote = RELAXED_NOTE_PREFIX + origReq.weekdayMaxMinutes() + RELAXED_NOTE_SUFFIX;
            req = new PlanRequest(origReq.week(), origReq.meals(), origReq.servings(), origReq.memberIds(),
                    null, origReq.budgetFen(), origReq.preferExpiring());
        }
        Map<Long, DishCandidate> byId = pool.stream()
                .collect(Collectors.toMap(DishCandidate::dishId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<LocalDate> days = req.week().days();
        int mealsPerDay = req.meals().size();

        List<PlannedSlot> slots = arranger.arrange(new ArrangeContext(req, profile, pool, expiringIngredientIds,
                days, List.of(), Set.of(), operatorId));
        List<PlanValidator.DayIssue> issues = PlanValidator.validate(slots, byId, profile, days, mealsPerDay);
        int rounds = 1;
        Set<Long> excluded = new HashSet<>();
        while (!issues.isEmpty() && rounds <= MAX_REWORK_ROUNDS) {
            Set<LocalDate> badDays = issues.stream().map(PlanValidator.DayIssue::date)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            issues.forEach(i -> excluded.addAll(i.offendingDishIds()));
            List<PlannedSlot> fixed = slots.stream().filter(s -> !badDays.contains(s.date())).toList();
            List<LocalDate> redoDays = days.stream().filter(badDays::contains).toList();
            Set<Long> fixedIds = fixed.stream().map(PlannedSlot::dishId).collect(Collectors.toSet());
            long available = pool.stream().filter(c -> !excluded.contains(c.dishId()) && !fixedIds.contains(c.dishId())).count();
            boolean shortage = available < (long) redoDays.size() * mealsPerDay; // 候选不够，回炉模型也变不出菜
            boolean overtime = Duration.ofMillis(clock.millis() - startMs).compareTo(llmReworkBudget) > 0;
            MealArranger rework = shortage || overtime ? cheapArranger : arranger;
            List<PlannedSlot> redo = rework.arrange(new ArrangeContext(req, profile, pool, expiringIngredientIds,
                    redoDays, fixed, excluded, operatorId));
            List<PlannedSlot> merged = new ArrayList<>(fixed);
            merged.addAll(redo);
            merged.sort(Comparator.comparing(PlannedSlot::date).thenComparing(PlannedSlot::meal));
            slots = merged;
            rounds++;
            issues = PlanValidator.validate(slots, byId, profile, days, mealsPerDay);
        }
        PlanCheck check = summarize(slots, byId, profile, issues, rounds);
        if (relaxedNote != null) {
            List<String> notes = new ArrayList<>(check.notes());
            notes.add(relaxedNote);
            check = new PlanCheck(check.passed(), check.rounds(), notes);
        }
        return new Outcome(slots, check, req);
    }

    /** 过滤后的正菜里，满足工作日时长上限的数量够不够排满工作日（不重样）。 */
    static boolean enoughWeekdayDishes(PlanRequest req, List<DishCandidate> pool) {
        long weekdays = req.week().days().stream().filter(WeekRange::isWeekday).count();
        long need = weekdays * req.meals().size();
        long fit = pool.stream().filter(PlanValidator::isMainDish)
                .filter(c -> c.minutes() == null || c.minutes() <= req.weekdayMaxMinutes()).count();
        return fit >= need;
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
                                    PlanRequest req, int rounds, PlanCheck previous) {
        Map<Long, DishCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(DishCandidate::dishId, Function.identity(), (a, b) -> a));
        List<PlanValidator.DayIssue> issues = PlanValidator.validate(slots, byId, profile, req.week().days(),
                req.meals().size());
        PlanCheck check = summarize(slots, byId, profile, issues, rounds);
        // 保留排餐时「工作日已放宽时长」的说明
        List<String> relaxed = previous == null ? List.of() : previous.notes().stream()
                .filter(n -> n.startsWith(RELAXED_NOTE_PREFIX) && n.endsWith(RELAXED_NOTE_SUFFIX)).toList();
        if (relaxed.isEmpty()) return check;
        List<String> notes = new ArrayList<>(check.notes());
        notes.addAll(relaxed);
        return new PlanCheck(check.passed(), check.rounds(), notes);
    }
}
