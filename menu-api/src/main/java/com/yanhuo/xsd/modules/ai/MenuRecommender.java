package com.yanhuo.xsd.modules.ai;

import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 菜单推荐纯函数（算法地基，不依赖外部状态；参照 MenuCalcService 范式）。
 *
 * <p>职责：过滤（健康指标上限 + 过敏食材）→ 打分（蛋白含量为主，达标奖励）→ 组合（控制总价 ≤ 预算）→
 * 按 scope 限量（DAY 1 组 / WEEK 3 组）。候选池查菜 + 营养是 IO，由 AiService 在调用前组装成
 * {@link CandidateDish} 传入；本类只做确定性算法。
 *
 * <p>seed 用于在多个等分组之间做确定性选择，避免同一输入每次返回不同候选顺序。
 */
@Component
public class MenuRecommender {

    /** 健康约束：每道菜的营养上限（null 表示不约束）。当前支持 sugarMax(5) / calMax(1)。 */
    public record Constraints(BigDecimal sugarMax, BigDecimal calMax) {}

    /**
     * 候选菜（AiService 已查好的组装结构）：营养按 metricId -> per 份值（非 per100g，已按份数折算）。
     *
     * @param id            dishId
     * @param name          菜名
     * @param price         单价（1 份）
     * @param nutrition     per 份营养 metricId -> 值
     * @param ingredients   食材名列表（用于过敏过滤）
     */
    public record CandidateDish(Long id, String name, BigDecimal price,
                                 Map<Long, BigDecimal> nutrition, List<String> ingredients) {}

    private static final long METRIC_SUGAR = 5L;
    private static final long METRIC_CAL = 1L;
    private static final long METRIC_PROTEIN = 2L;
    private static final String SOURCE = "mock";

    /**
     * 推荐：过滤 → 打分 → 组合 → 限量。
     *
     * @param candidates 候选菜池（已查营养/食材，IO 在 AiService 完成）
     * @param cons       健康约束（null 字段不约束）
     * @param allergies  过敏食材名（菜名/食材名命中即剔）
     * @param budget     总预算上限（每组 totalPrice ≤ budget）；null 视为无上限
     * @param scope      DAY（1 组）/ WEEK（3 组）；其他按 DAY
     * @param seed       等分确定性扰动种子
     * @return 推荐候选组（已按 score 降序）
     */
    public List<MenuCandidate> recommend(List<CandidateDish> candidates, Constraints cons,
                                         List<String> allergies, BigDecimal budget,
                                         String scope, long seed) {
        // 1. 过滤：健康上限 + 过敏
        List<CandidateDish> pool = new ArrayList<>();
        Set<String> allergy = allergies == null ? Set.of() : new HashSet<>(allergies);
        for (CandidateDish d : candidates) {
            if (!healthOk(d, cons)) continue;
            if (!allergyOk(d, allergy)) continue;
            pool.add(d);
        }
        if (pool.isEmpty()) return List.of();

        // 2. 组合：单菜 + 两菜配对，过滤超预算
        List<List<CandidateDish>> combos = new ArrayList<>();
        for (CandidateDish d : pool) {
            if (withinBudget(List.of(d), budget)) combos.add(List.of(d));
        }
        for (int i = 0; i < pool.size(); i++) {
            for (int j = i + 1; j < pool.size(); j++) {
                List<CandidateDish> pair = List.of(pool.get(i), pool.get(j));
                if (withinBudget(pair, budget)) combos.add(pair);
            }
        }
        if (combos.isEmpty()) return List.of();

        // 3. 打分
        List<Scored> scored = new ArrayList<>();
        for (List<CandidateDish> combo : combos) {
            scored.add(new Scored(combo, score(combo, cons)));
        }
        // 4. 排序：score 降序，等分用 seed 确定性扰动（避免每次顺序不同）
        Random rnd = new Random(seed);
        scored.sort((a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return Integer.compare(rnd.nextInt(), rnd.nextInt());
        });

        // 5. 限量 + 去重（DAY 1 组 / WEEK 3 组），尽量让组之间菜不同
        int limit = "WEEK".equalsIgnoreCase(scope) ? 3 : 1;
        List<MenuCandidate> result = new ArrayList<>();
        Set<Long> usedDishes = new HashSet<>();
        // 第一轮：贪心选互不重叠的组
        for (Scored s : scored) {
            if (result.size() >= limit) break;
            if (s.dishes.stream().anyMatch(d -> usedDishes.contains(d.id()))) continue;
            result.add(toCandidate(s));
            s.dishes.forEach(d -> usedDishes.add(d.id()));
        }
        // 第二轮：若凑不齐 limit，放宽允许重叠
        for (Scored s : scored) {
            if (result.size() >= limit) break;
            result.add(toCandidate(s));
        }
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    // ---------------- 过滤 ----------------

    /** 单菜营养是否在约束内：sugarMax / calMax 命中即超剔除。 */
    private static boolean healthOk(CandidateDish d, Constraints cons) {
        if (cons == null) return true;
        if (cons.sugarMax() != null) {
            BigDecimal v = d.nutrition() == null ? null : d.nutrition().get(METRIC_SUGAR);
            if (v != null && v.compareTo(cons.sugarMax()) > 0) return false;
        }
        if (cons.calMax() != null) {
            BigDecimal v = d.nutrition() == null ? null : d.nutrition().get(METRIC_CAL);
            if (v != null && v.compareTo(cons.calMax()) > 0) return false;
        }
        return true;
    }

    /** 菜名/食材是否不含过敏食材。 */
    private static boolean allergyOk(CandidateDish d, Set<String> allergy) {
        if (allergy.isEmpty()) return true;
        if (d.name() != null && containsAny(d.name(), allergy)) return false;
        if (d.ingredients() != null) {
            for (String ing : d.ingredients()) {
                if (containsAny(ing, allergy)) return false;
            }
        }
        return true;
    }

    private static boolean containsAny(String s, Set<String> subs) {
        for (String sub : subs) {
            if (sub != null && !sub.isEmpty() && s.contains(sub)) return true;
        }
        return false;
    }

    // ---------------- 预算 / 打分 ----------------

    private static boolean withinBudget(List<CandidateDish> combo, BigDecimal budget) {
        if (budget == null) return true;
        return totalPrice(combo).compareTo(budget) <= 0;
    }

    private static BigDecimal totalPrice(List<CandidateDish> combo) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CandidateDish d : combo) {
            sum = sum.add(d.price() == null ? BigDecimal.ZERO : d.price());
        }
        return sum;
    }

    /** 打分：蛋白总量为主，组合菜数小奖励（鼓励搭配）。 */
    private static double score(List<CandidateDish> combo, Constraints cons) {
        double protein = 0;
        for (CandidateDish d : combo) {
            BigDecimal p = d.nutrition() == null ? null : d.nutrition().get(METRIC_PROTEIN);
            if (p != null) protein += p.doubleValue();
        }
        // 搭配奖励：两菜比单菜略加分
        double comboBonus = combo.size() >= 2 ? 1.0 : 0.0;
        return protein + comboBonus;
    }

    // ---------------- 组装 ----------------

    private static MenuCandidate toCandidate(Scored s) {
        List<MenuCandidate.DishItem> items = new ArrayList<>();
        for (CandidateDish d : s.dishes) {
            items.add(new MenuCandidate.DishItem(d.id(), d.name(), BigDecimal.ONE, d.price()));
        }
        BigDecimal total = totalPrice(s.dishes);
        Map<Long, BigDecimal> nut = new java.util.HashMap<>();
        for (CandidateDish d : s.dishes) {
            if (d.nutrition() == null) continue;
            for (var e : d.nutrition().entrySet()) {
                nut.merge(e.getKey(), e.getValue(), BigDecimal::add);
            }
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("蛋白含量较高，营养均衡");
        if (s.dishes.size() >= 2) reasons.add("荤素搭配");
        return new MenuCandidate(items, total, nut, s.score, reasons, SOURCE);
    }

    private static final class Scored {
        final List<CandidateDish> dishes;
        final double score;
        Scored(List<CandidateDish> dishes, double score) {
            this.dishes = dishes; this.score = score;
        }
    }
}
