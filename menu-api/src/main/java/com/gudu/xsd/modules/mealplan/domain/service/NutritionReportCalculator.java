package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.NutrientKey;
import com.gudu.xsd.modules.mealplan.domain.model.Nutrients;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 营养报告计算（纯函数）：按「有食记的天」求日均（没记录的天不摊薄），对照成员阈值出雷达、
 * 超标提醒（主要来源菜 + 同约束下更低的替换菜）、钠趋势与做得好的项。
 */
public final class NutritionReportCalculator {

    public static final int MAX_SOURCES = 2;
    public static final int MAX_SUGGESTIONS = 2;
    public static final double GOOD_RATIO = 0.9;

    /** 一条食记折算到该成员的摄入（1 人份 × 份数系数）。 */
    public record Intake(LocalDate date, long dishId, Nutrients perPerson) {
    }

    public record RadarItem(NutrientKey key, double actual, double target, double ratio) {
    }

    public record Alert(NutrientKey key, double dailyAvg, double limit, List<Long> sourceDishIds,
                        List<Long> suggestionDishIds) {
    }

    public record TrendPoint(LocalDate date, double value) {
    }

    public record Result(int recordCount, int activeDays, List<RadarItem> radar, List<Alert> alerts,
                         List<TrendPoint> sodiumTrend, double sodiumLimit, List<String> goods) {
    }

    private NutritionReportCalculator() {
    }

    public static Result compute(List<Intake> intakes, Map<NutrientKey, Double> targets, HouseholdProfile memberProfile,
                                 List<DishCandidate> catalog) {
        Map<LocalDate, Nutrients> byDay = new TreeMap<>();
        Map<Long, Nutrients> byDish = new HashMap<>();
        for (Intake i : intakes) {
            byDay.merge(i.date(), i.perPerson(), Nutrients::plus);
            byDish.merge(i.dishId(), i.perPerson(), Nutrients::plus);
        }
        int activeDays = byDay.size();
        Nutrients total = byDay.values().stream().reduce(Nutrients.ZERO, Nutrients::plus);
        Nutrients avg = activeDays == 0 ? Nutrients.ZERO : total.scale(1.0 / activeDays);

        List<RadarItem> radar = new ArrayList<>();
        List<Alert> alerts = new ArrayList<>();
        List<String> goods = new ArrayList<>();
        for (NutrientKey k : NutrientKey.values()) {
            double target = targets.getOrDefault(k, 0.0);
            double actual = avg.get(k);
            double ratio = target <= 0 ? 0 : actual / target;
            radar.add(new RadarItem(k, round1(actual), round1(target), round2(ratio)));
            if (activeDays == 0) continue;
            if (k.isLimit() && actual > target) {
                List<Long> sources = byDish.entrySet().stream()
                        .sorted(Comparator.comparingDouble((Map.Entry<Long, Nutrients> e) -> -e.getValue().get(k))
                                .thenComparing(Map.Entry::getKey))
                        .limit(MAX_SOURCES).map(Map.Entry::getKey).toList();
                List<Long> suggestions = catalog.stream()
                        .filter(c -> !sources.contains(c.dishId()) && CandidateFilter.allowed(c, memberProfile))
                        .filter(c -> c.perServing().get(k) > 0)
                        .sorted(Comparator.comparingDouble((DishCandidate c) -> c.perServing().get(k))
                                .thenComparingLong(DishCandidate::dishId))
                        .limit(MAX_SUGGESTIONS).map(DishCandidate::dishId).toList();
                alerts.add(new Alert(k, round1(actual), round1(target), sources, suggestions));
            } else if (!k.isLimit() && ratio >= GOOD_RATIO) {
                goods.add(k.label() + "达标");
            } else if (k == NutrientKey.SODIUM) {
                goods.add("钠控制在建议量内");
            }
        }
        List<TrendPoint> trend = byDay.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey(), round1(e.getValue().sodiumMg()))).toList();
        return new Result(intakes.size(), activeDays, radar, alerts, trend,
                targets.getOrDefault(NutrientKey.SODIUM, 0.0), goods);
    }

    static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
