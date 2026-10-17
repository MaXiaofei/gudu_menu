package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.Nutrients;
import com.gudu.xsd.modules.mealplan.domain.model.RecipeLine;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜品画像（纯函数）：按用料估算整道菜营养 → 1 人份（整道按 {@link #BATCH_SERVINGS} 人份计）、
 * 食材成本、荤素类别、宝宝可吃。
 *
 * <p>能量/蛋白/脂肪优先用食材库每 100g 值，缺失按品类兜底；食材库没有钠和膳食纤维指标，
 * 按调料关键词（盐、酱油、豆瓣酱…）与品类典型值估算——是「估算」，用于排餐约束与趋势提醒，不作医学用途。
 */
public final class DishProfiler {

    public static final int BATCH_SERVINGS = 3;
    static final double BABY_SODIUM_MAX_MG = 600;
    static final double MEAT_GRAMS_MIN = 80;
    /**
     * 咸口菜的调味钠下限（整道，约 3g 盐）：菜谱常漏关联「盐 适量」或用量为空，按用料算出的咸味调料钠
     * 不足时补到此值。不补会把红烧肉、酸菜鱼估成每份 100mg 以内，少盐约束形同虚设。甜品不补。
     */
    static final double DEFAULT_SEASONING_SODIUM_MG = 1200;
    static final double SALTY_PER_100G = 1000;

    /** 钠 mg/100g：关键词（长的在前）。 */
    private static final List<Map.Entry<String, Double>> SODIUM_BY_NAME = List.of(
            Map.entry("鸡精", 18000.0), Map.entry("鱼露", 9000.0), Map.entry("味精", 8160.0), Map.entry("咸菜", 7000.0),
            Map.entry("豆瓣酱", 6000.0), Map.entry("酱油", 5757.0), Map.entry("生抽", 5757.0), Map.entry("老抽", 5757.0),
            Map.entry("虾皮", 5000.0), Map.entry("榨菜", 4200.0), Map.entry("蚝油", 4000.0), Map.entry("火锅底料", 4000.0),
            Map.entry("雪菜", 3300.0), Map.entry("剁椒", 3000.0), Map.entry("咸鸭蛋", 2700.0), Map.entry("咸蛋", 2700.0),
            Map.entry("辣酱", 2000.0), Map.entry("酸菜", 1000.0), Map.entry("泡菜", 700.0),
            Map.entry("黄豆酱", 3000.0), Map.entry("豆豉", 2500.0), Map.entry("腐乳", 2400.0), Map.entry("甜面酱", 2100.0),
            Map.entry("腊肠", 2000.0), Map.entry("腊肉", 2000.0), Map.entry("培根", 1500.0), Map.entry("咖喱", 1500.0),
            Map.entry("火腿", 1000.0), Map.entry("番茄酱", 1000.0), Map.entry("料酒", 500.0), Map.entry("醋", 260.0),
            Map.entry("盐", 39311.0));

    private static final Map<String, Double> SODIUM_BY_CATEGORY = Map.ofEntries(
            Map.entry("蔬菜", 40.0), Map.entry("水果", 2.0), Map.entry("畜禽肉", 70.0), Map.entry("水产海鲜", 150.0),
            Map.entry("蛋类", 130.0), Map.entry("豆制品", 10.0), Map.entry("乳制品", 50.0), Map.entry("谷物主食", 5.0),
            Map.entry("调味料", 500.0), Map.entry("坚果", 5.0), Map.entry("干菌藻", 50.0), Map.entry("速冻", 400.0));

    /** 膳食纤维 g/100g。 */
    private static final List<Map.Entry<String, Double>> FIBER_BY_NAME = List.of(
            Map.entry("燕麦", 10.0), Map.entry("黄豆", 15.0), Map.entry("木耳", 2.6), Map.entry("香菇", 3.3),
            Map.entry("菌", 2.5), Map.entry("菇", 2.5), Map.entry("西兰花", 1.6), Map.entry("西蓝花", 1.6),
            Map.entry("菠菜", 1.7), Map.entry("芹菜", 1.2), Map.entry("南瓜", 0.8), Map.entry("糙米", 3.0),
            Map.entry("小米", 1.6), Map.entry("玉米", 2.9), Map.entry("红薯", 1.6), Map.entry("山药", 0.8));

    private static final Map<String, Double> FIBER_BY_CATEGORY = Map.ofEntries(
            Map.entry("蔬菜", 1.5), Map.entry("水果", 1.5), Map.entry("豆制品", 0.5), Map.entry("谷物主食", 1.0),
            Map.entry("干菌藻", 25.0), Map.entry("坚果", 8.0));

    /** 品类兜底 [能量 kcal, 蛋白 g, 脂肪 g] / 100g。 */
    private static final Map<String, double[]> MACRO_BY_CATEGORY = Map.ofEntries(
            Map.entry("畜禽肉", new double[]{250, 18, 20}), Map.entry("水产海鲜", new double[]{100, 18, 2}),
            Map.entry("蛋类", new double[]{144, 13, 9}), Map.entry("豆制品", new double[]{80, 8, 4}),
            Map.entry("蔬菜", new double[]{25, 1.5, 0.3}), Map.entry("水果", new double[]{50, 0.6, 0.2}),
            Map.entry("谷物主食", new double[]{350, 8, 1.5}), Map.entry("乳制品", new double[]{65, 3, 3.5}),
            Map.entry("坚果", new double[]{600, 20, 50}), Map.entry("干菌藻", new double[]{200, 15, 1.5}));

    /** 成本：分 / 500g（调料不计）。 */
    private static final Map<String, Integer> PRICE_PER_JIN = Map.ofEntries(
            Map.entry("畜禽肉", 2500), Map.entry("水产海鲜", 3500), Map.entry("蛋类", 600), Map.entry("豆制品", 500),
            Map.entry("蔬菜", 400), Map.entry("水果", 800), Map.entry("乳制品", 1200), Map.entry("谷物主食", 300),
            Map.entry("干菌藻", 3000), Map.entry("坚果", 3000), Map.entry("速冻", 1500));

    private static final Set<String> SPICY_TAGS = Set.of("微辣", "中辣", "特辣", "重口味", "卤味", "烧烤");
    private static final Set<String> SOFT_TAGS = Set.of("蒸", "清淡", "炖", "汤", "宝宝");
    private static final List<String> SPICY_WORDS = List.of("辣椒", "花椒", "豆瓣酱", "麻辣", "剁椒", "辣");
    private static final List<String> STAPLE_WORDS = List.of("饭", "面", "粥", "饼", "粉", "饺", "包子", "馄饨");
    private static final List<String> SWEET_WORDS = List.of("冰糖", "糖水", "甜品", "布丁", "蛋糕", "饼干", "慕斯",
            "双皮奶", "炖梨", "山药泥", "红豆", "绿豆汤", "奶昔", "西米", "银耳", "芋圆", "果冻", "冰粉");

    private DishProfiler() {
    }

    /** 整道菜营养估算。 */
    public static Nutrients batch(List<RecipeLine> lines) {
        Nutrients total = Nutrients.ZERO;
        for (RecipeLine l : lines == null ? List.<RecipeLine>of() : lines) {
            double g = PortionEstimator.grams(l);
            double f = g / 100.0;
            double[] macro = macro(l);
            total = total.plus(new Nutrients(macro[0] * f, macro[1] * f, macro[2] * f,
                    sodiumPer100g(l) * f, fiberPer100g(l) * f));
        }
        return total;
    }

    public static Nutrients perServing(List<RecipeLine> lines) {
        return batch(lines).scale(1.0 / BATCH_SERVINGS);
    }

    public static int costFen(List<RecipeLine> lines) {
        double fen = 0;
        for (RecipeLine l : lines == null ? List.<RecipeLine>of() : lines) {
            Integer price = l.categoryName() == null ? null : PRICE_PER_JIN.get(l.categoryName());
            if (price != null) fen += PortionEstimator.grams(l) / 500.0 * price;
        }
        return (int) Math.round(fen);
    }

    public static DishKind kind(String name, Set<String> tags, List<RecipeLine> lines) {
        String n = name == null ? "" : name;
        if (sweet(n, tags)) return DishKind.DESSERT;
        if (tags.contains("汤") || n.endsWith("汤") || n.endsWith("羹")) return DishKind.SOUP;
        if (tags.contains("饭") || STAPLE_WORDS.stream().anyMatch(n::contains)) return DishKind.STAPLE;
        if (tags.contains("荤菜")) return DishKind.MEAT;
        if (tags.contains("素菜")) return DishKind.VEG;
        double meat = 0;
        for (RecipeLine l : lines == null ? List.<RecipeLine>of() : lines) {
            if ("畜禽肉".equals(l.categoryName()) || "水产海鲜".equals(l.categoryName())) meat += PortionEstimator.grams(l);
        }
        return meat >= MEAT_GRAMS_MIN ? DishKind.MEAT : DishKind.VEG;
    }

    /** 宝宝可吃：标了「宝宝」直接算；有辣/重口味不算；否则需软烂做法（蒸/炖/汤/清淡/粥羹）且 1 人份钠 ≤ 600mg。 */
    public static boolean babyFriendly(String name, Set<String> tags, List<String> ingredientNames, Nutrients perServing) {
        if (tags.contains("宝宝")) return true;
        if (tags.stream().anyMatch(SPICY_TAGS::contains)) return false;
        String n = name == null ? "" : name;
        if (SPICY_WORDS.stream().anyMatch(n::contains)
                || ingredientNames.stream().anyMatch(i -> SPICY_WORDS.stream().anyMatch(i::contains))) return false;
        boolean soft = tags.stream().anyMatch(SOFT_TAGS::contains) || n.contains("粥") || n.contains("羹")
                || n.contains("蒸") || n.endsWith("泥");
        return soft && perServing.sodiumMg() <= BABY_SODIUM_MAX_MG;
    }

    /** 组装候选。 */
    public static DishCandidate candidate(long dishId, String name, Integer minutes, Set<String> tags,
                                          Set<Long> ingredientIds, List<RecipeLine> lines) {
        Set<String> t = tags == null ? Set.of() : tags;
        Nutrients per = perServing(lines);
        double salty = saltySeasoningSodium(lines);
        if (!sweet(name, t) && salty < DEFAULT_SEASONING_SODIUM_MG) {
            per = per.plus(new Nutrients(0, 0, 0, (DEFAULT_SEASONING_SODIUM_MG - salty) / BATCH_SERVINGS, 0));
        }
        List<String> names = lines == null ? List.of() : lines.stream().map(RecipeLine::name).filter(x -> x != null).toList();
        return new DishCandidate(dishId, name, minutes, t, ingredientIds, names, kind(name, t, lines), per,
                costFen(lines), babyFriendly(name, t, names, per));
    }

    /** 甜品/糖水（按名称关键词或「烘焙」标签）。 */
    public static boolean sweet(String name, Set<String> tags) {
        String n = name == null ? "" : name;
        return (tags != null && tags.contains("烘焙")) || SWEET_WORDS.stream().anyMatch(n::contains);
    }

    /** 咸味调料（盐/酱油/各种酱，钠 ≥ 1000mg/100g）贡献的整道钠。 */
    static double saltySeasoningSodium(List<RecipeLine> lines) {
        double s = 0;
        for (RecipeLine l : lines == null ? List.<RecipeLine>of() : lines) {
            double per100 = sodiumPer100g(l);
            if (per100 >= SALTY_PER_100G) s += per100 * PortionEstimator.grams(l) / 100.0;
        }
        return s;
    }

    static double sodiumPer100g(RecipeLine l) {
        String n = l.name() == null ? "" : l.name();
        for (Map.Entry<String, Double> e : SODIUM_BY_NAME) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        return l.categoryName() == null ? 50 : SODIUM_BY_CATEGORY.getOrDefault(l.categoryName(), 50.0);
    }

    static double fiberPer100g(RecipeLine l) {
        String n = l.name() == null ? "" : l.name();
        for (Map.Entry<String, Double> e : FIBER_BY_NAME) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        return l.categoryName() == null ? 0 : FIBER_BY_CATEGORY.getOrDefault(l.categoryName(), 0.0);
    }

    static double[] macro(RecipeLine l) {
        String n = l.name() == null ? "" : l.name();
        double[] fallback;
        if (n.contains("油") && !n.contains("酱油") && !n.contains("蚝油")) {
            fallback = new double[]{899, 0, 100};
        } else if (n.contains("糖")) {
            fallback = new double[]{400, 0, 0};
        } else {
            fallback = l.categoryName() == null ? new double[]{50, 2, 1}
                    : MACRO_BY_CATEGORY.getOrDefault(l.categoryName(), new double[]{50, 2, 1});
        }
        return new double[]{
                l.energyPer100g() != null ? l.energyPer100g() : fallback[0],
                l.proteinPer100g() != null ? l.proteinPer100g() : fallback[1],
                l.fatPer100g() != null ? l.fatPer100g() : fallback[2]};
    }
}
