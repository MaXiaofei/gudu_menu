package com.gudu.xsd.modules.ai;

import com.gudu.xsd.modules.ai.dto.MenuCandidate;
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
 * <p>职责：过滤（健康指标上限 + 过敏食材）→ 打分（蛋白含量为主，达标奖励）→ 组合 →
 * 按 scope 限量（DAY 1 组 / WEEK 3 组）。候选池查菜 + 营养是 IO，由 AiService 在调用前组装成
 * {@link CandidateDish} 传入；本类只做确定性算法。
 *
 * <p>V55（食材去单位）：预算（budget）约束随价格链路删除，组合不再按总价过滤。
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
     * @param nutrition     per 份营养 metricId -> 值
     * @param ingredients   食材名列表（用于过敏过滤）
     */
    public record CandidateDish(Long id, String name,
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
     * @param scope      DAY（1 组）/ WEEK（3 组）；其他按 DAY
     * @param seed       等分确定性扰动种子
     * @return 推荐候选组（已按 score 降序）
     */
    public List<MenuCandidate> recommend(List<CandidateDish> candidates, Constraints cons,
                                         List<String> allergies,
                                         String scope, long seed) {
        // 1. 过滤：过敏原硬过滤（食物过敏不能软化）；健康约束改为软扣分（不排除）
        List<CandidateDish> pool = new ArrayList<>();
        Set<String> allergy = allergies == null ? Set.of() : new HashSet<>(allergies);
        for (CandidateDish d : candidates) {
            if (!allergyOk(d, allergy)) continue;  // 过敏原仍硬过滤
            pool.add(d);  // 健康超标的不排除，进打分扣分
        }
        if (pool.isEmpty()) return List.of();

        // 2. 组合：单菜 + 两菜配对，双超标排除（V55：预算过滤已删）
        List<List<CandidateDish>> combos = new ArrayList<>();
        for (CandidateDish d : pool) {
            combos.add(List.of(d));
        }
        for (int i = 0; i < pool.size(); i++) {
            for (int j = i + 1; j < pool.size(); j++) {
                List<CandidateDish> pair = List.of(pool.get(i), pool.get(j));
                // 配额规则：两道都超标 → 排除该组合
                if (comboOverLimit(pair, cons)) continue;
                combos.add(pair);
            }
        }
        if (combos.isEmpty()) return List.of();

        // 3. 打分（含软扣分）
        List<Scored> scored = new ArrayList<>();
        for (List<CandidateDish> combo : combos) {
            scored.add(new Scored(combo, score(combo, cons)));
        }
        // 4. 排序：score 降序，等分用 seed 确定性扰动
        Random rnd = new Random(seed);
        scored.sort((a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return Integer.compare(rnd.nextInt(), rnd.nextInt());
        });

        // 5. 限量 + 去重
        int limit = "WEEK".equalsIgnoreCase(scope) ? 3 : 1;
        List<MenuCandidate> result = new ArrayList<>();
        Set<Long> usedDishes = new HashSet<>();
        for (Scored s : scored) {
            if (result.size() >= limit) break;
            if (s.dishes.stream().anyMatch(d -> usedDishes.contains(d.id()))) continue;
            result.add(toCandidate(s));
            s.dishes.forEach(d -> usedDishes.add(d.id()));
        }
        for (Scored s : scored) {
            if (result.size() >= limit) break;
            result.add(toCandidate(s));
        }
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    // ---------------- 软约束：配额 + 组合总量 ----------------

    /** 判断单道菜是否超标（用于配额判断）。 */
    private static boolean isOverLimit(CandidateDish d, Constraints cons) {
        if (cons == null) return false;
        if (cons.sugarMax() != null) {
            BigDecimal v = d.nutrition() == null ? null : d.nutrition().get(METRIC_SUGAR);
            if (v != null && v.compareTo(cons.sugarMax()) > 0) return true;
        }
        if (cons.calMax() != null) {
            BigDecimal v = d.nutrition() == null ? null : d.nutrition().get(METRIC_CAL);
            if (v != null && v.compareTo(cons.calMax()) > 0) return true;
        }
        return false;
    }

    /** 配额规则：组合中超过 1 道菜超标 → 排除该组合。 */
    private static boolean comboOverLimit(List<CandidateDish> combo, Constraints cons) {
        if (cons == null) return false;
        int overCount = 0;
        for (CandidateDish d : combo) {
            if (isOverLimit(d, cons)) overCount++;
        }
        return overCount > 1;
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

    // ---------------- 打分 ----------------

    /**
     * 打分：蛋白总量为主 + 搭配奖励 + 健康软扣分。
     * 软扣分：每道超标菜扣偏离比例的惩罚分，但不归零（偶尔可出现）。
     */
    private static double score(List<CandidateDish> combo, Constraints cons) {
        double protein = 0;
        for (CandidateDish d : combo) {
            BigDecimal p = d.nutrition() == null ? null : d.nutrition().get(METRIC_PROTEIN);
            if (p != null) protein += p.doubleValue();
        }
        double comboBonus = combo.size() >= 2 ? 1.0 : 0.0;
        // 软扣分：超标菜按偏离程度扣分
        double penalty = 0;
        if (cons != null) {
            for (CandidateDish d : combo) {
                penalty += overLimitPenalty(d, cons);
            }
        }
        return protein + comboBonus - penalty;
    }

    /** 超标惩罚：偏离越大扣越多。轻微超标扣少，严重超标扣多。 */
    private static double overLimitPenalty(CandidateDish d, Constraints cons) {
        double penalty = 0;
        if (cons.sugarMax() != null && cons.sugarMax().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal v = d.nutrition() == null ? null : d.nutrition().get(METRIC_SUGAR);
            if (v != null && v.compareTo(cons.sugarMax()) > 0) {
                double ratio = v.doubleValue() / cons.sugarMax().doubleValue();
                penalty += (ratio - 1.0) * 5.0; // 超标 10% 扣 0.5 分，超标 100% 扣 5 分
            }
        }
        if (cons.calMax() != null && cons.calMax().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal v = d.nutrition() == null ? null : d.nutrition().get(METRIC_CAL);
            if (v != null && v.compareTo(cons.calMax()) > 0) {
                double ratio = v.doubleValue() / cons.calMax().doubleValue();
                penalty += (ratio - 1.0) * 5.0;
            }
        }
        return penalty;
    }

    // ---------------- 组装 ----------------

    private static MenuCandidate toCandidate(Scored s) {
        List<MenuCandidate.DishItem> items = new ArrayList<>();
        for (CandidateDish d : s.dishes) {
            items.add(new MenuCandidate.DishItem(d.id(), d.name(), BigDecimal.ONE));
        }
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
        return new MenuCandidate(items, nut, s.score, reasons, SOURCE);
    }

    private static final class Scored {
        final List<CandidateDish> dishes;
        final double score;
        Scored(List<CandidateDish> dishes, double score) {
            this.dishes = dishes; this.score = score;
        }
    }
}
