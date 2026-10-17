package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.Meal;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.SlotBadge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启发式排布（确定性，可复现）：逐天逐餐贪心打分。
 * <ul>
 *   <li>快到期优先：用到未被前面格子用掉的快到期食材 +10/样，越靠前的天权重越高；</li>
 *   <li>荤素搭配：同一天上一格是荤菜则偏好素菜/汤，反之偏好荤菜；同类连续出现扣分；</li>
 *   <li>宝宝：家里有宝宝时每天至少一格宝宝可吃（午餐优先，最后一格强制）；</li>
 *   <li>少盐：1 人份钠每 200mg 扣 1 分；预算：超出平均每格预算的部分扣分；糖水甜品大幅降权；</li>
 *   <li>不重样：本周已出现、或被本轮排除的菜不再选。</li>
 * </ul>
 */
public final class HeuristicMealArranger implements MealArranger {

    static final double EXPIRING_WEIGHT = 10;
    static final double BALANCE_BONUS = 4;
    static final double SAME_KIND_PENALTY = 3;
    static final double BABY_BONUS = 6;
    static final double BABY_FORCE_BONUS = 30;
    static final double SODIUM_STEP_MG = 200;
    static final double BUDGET_STEP_FEN = 200;
    static final double DESSERT_PENALTY = 12;
    static final int SIMILAR_MIN_LEN = 3;

    @Override
    public List<PlannedSlot> arrange(ArrangeContext ctx) {
        List<Meal> meals = ctx.request().meals();
        Set<Long> used = new HashSet<>();
        List<String> usedNames = new ArrayList<>();
        Set<Long> expiringLeft = new HashSet<>(ctx.expiringIngredientIds());
        int fixedCost = 0;
        for (PlannedSlot s : ctx.fixed()) {
            used.add(s.dishId());
            DishCandidate c = find(ctx.pool(), s.dishId());
            if (c != null) {
                usedNames.add(c.name());
                expiringLeft.removeAll(c.ingredientIds());
                fixedCost += c.costFen();
            }
        }
        int slotsToFill = ctx.days().size() * meals.size();
        Integer budget = ctx.request().budgetFen();
        int remainingBudget = budget == null ? Integer.MAX_VALUE : Math.max(0, budget - fixedCost);
        int remainingSlots = slotsToFill;
        boolean babyNeeded = ctx.profile().hasBaby();
        List<LocalDate> weekDays = ctx.request().week().days();

        List<PlannedSlot> out = new ArrayList<>();
        for (LocalDate day : ctx.days()) {
            DishKind prevKind = null;
            boolean dayHasBaby = false;
            double dayWeight = 1.0 - (double) weekDays.indexOf(day) / weekDays.size();
            for (int mi = 0; mi < meals.size(); mi++) {
                Meal meal = meals.get(mi);
                boolean lastOfDay = mi == meals.size() - 1;
                double avgBudget = budget == null ? Double.MAX_VALUE : (double) remainingBudget / Math.max(1, remainingSlots);
                DishCandidate best = null;
                double bestScore = Double.NEGATIVE_INFINITY;
                for (DishCandidate c : ctx.pool()) {
                    if (used.contains(c.dishId()) || ctx.excludedDishIds().contains(c.dishId())) continue;
                    if (similarToUsed(c.name(), usedNames)) continue; // 红烧肉 / 秘制红烧肉 视为重样
                    if (!CandidateFilter.fitsTime(c, day, ctx.request().weekdayMaxMinutes())) continue;
                    double score = score(c, ctx, expiringLeft, dayWeight, prevKind, babyNeeded && !dayHasBaby,
                            lastOfDay, meal, avgBudget);
                    if (score > bestScore || (score == bestScore && best != null && c.dishId() < best.dishId())) {
                        best = c;
                        bestScore = score;
                    }
                }
                if (best == null) continue; // 候选不够：留空，由校验报告
                Set<SlotBadge> badges = EnumSet.noneOf(SlotBadge.class);
                if (ctx.request().preferExpiring() && best.ingredientIds().stream().anyMatch(expiringLeft::contains)) {
                    badges.add(SlotBadge.EXPIRING);
                }
                if (babyNeeded && !dayHasBaby && best.babyFriendly()) {
                    badges.add(SlotBadge.BABY);
                    dayHasBaby = true;
                }
                out.add(new PlannedSlot(day, meal, best.dishId(), badges));
                used.add(best.dishId());
                usedNames.add(best.name());
                expiringLeft.removeAll(best.ingredientIds());
                prevKind = best.kind();
                remainingBudget = Math.max(0, remainingBudget - best.costFen());
                remainingSlots--;
            }
        }
        return out;
    }

    private static double score(DishCandidate c, ArrangeContext ctx, Set<Long> expiringLeft, double dayWeight,
                                DishKind prevKind, boolean babyPending, boolean lastOfDay, Meal meal, double avgBudget) {
        double s = 0;
        if (ctx.request().preferExpiring()) {
            long hits = c.ingredientIds().stream().filter(expiringLeft::contains).count();
            s += hits * EXPIRING_WEIGHT * (0.5 + dayWeight);
        }
        if (prevKind != null) {
            boolean balanced = prevKind == DishKind.MEAT ? c.kind() != DishKind.MEAT : c.kind() == DishKind.MEAT;
            s += balanced ? BALANCE_BONUS : 0;
            if (c.kind() == prevKind) s -= SAME_KIND_PENALTY;
        } else if (c.kind() == DishKind.MEAT && meal == Meal.DINNER) {
            s += 1; // 只排晚餐时略偏好荤菜
        }
        if (babyPending && c.babyFriendly()) s += lastOfDay ? BABY_FORCE_BONUS : (meal == Meal.LUNCH ? BABY_BONUS : 0);
        if (c.kind() == DishKind.DESSERT) s -= DESSERT_PENALTY; // 正餐不排糖水甜品，除非实在没得选
        s -= c.perServing().sodiumMg() / SODIUM_STEP_MG;
        if (avgBudget != Double.MAX_VALUE && c.costFen() > avgBudget) s -= (c.costFen() - avgBudget) / BUDGET_STEP_FEN;
        return s;
    }

    /** 名称互相包含（≥3 字）视为同一道菜的变体。 */
    static boolean similarToUsed(String name, List<String> usedNames) {
        if (name == null || name.length() < SIMILAR_MIN_LEN) return false;
        for (String u : usedNames) {
            if (u == null || u.length() < SIMILAR_MIN_LEN) continue;
            if (name.contains(u) || u.contains(name)) return true;
        }
        return false;
    }

    private static DishCandidate find(List<DishCandidate> pool, long dishId) {
        for (DishCandidate c : pool) {
            if (c.dishId() == dishId) return c;
        }
        return null;
    }
}
