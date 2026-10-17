package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.RecipeLine;

import java.util.List;
import java.util.Map;

/**
 * 用量 → 克数估算（菜谱用料单位五花八门：克/斤/勺/个/适量…）。V55 后用料不再换算克数，
 * 营养与成本估算统一走这里：重量/容量单位直接换算；计数单位按食材名或品类给单件克数；
 * 「适量/少许」按调料/油/其它给小份额。单行上限 2kg，防脏数据放大。
 */
public final class PortionEstimator {

    static final double MAX_GRAMS = 2000;
    static final double PIECE_OR_GRAM_THRESHOLD = 20;

    private static final Map<String, Double> WEIGHT_UNITS = Map.ofEntries(
            Map.entry("克", 1.0), Map.entry("g", 1.0), Map.entry("G", 1.0), Map.entry("毫升", 1.0), Map.entry("ml", 1.0),
            Map.entry("ML", 1.0), Map.entry("千克", 1000.0), Map.entry("公斤", 1000.0), Map.entry("kg", 1000.0),
            Map.entry("斤", 500.0), Map.entry("两", 50.0), Map.entry("升", 1000.0), Map.entry("L", 1000.0));

    private static final Map<String, Double> SPOON_UNITS = Map.ofEntries(
            Map.entry("大勺", 15.0), Map.entry("汤匙", 15.0), Map.entry("大匙", 15.0), Map.entry("汤勺", 15.0),
            Map.entry("勺", 10.0), Map.entry("匙", 10.0), Map.entry("小勺", 5.0), Map.entry("茶匙", 5.0),
            Map.entry("小匙", 5.0), Map.entry("杯", 200.0), Map.entry("碗", 250.0));

    /** 单件克数：按食材名关键词（长的优先）。 */
    private static final List<Map.Entry<String, Double>> PIECE_BY_NAME = List.of(
            Map.entry("鹌鹑蛋", 10.0), Map.entry("鸡蛋", 50.0), Map.entry("鸭蛋", 70.0), Map.entry("蛋", 50.0),
            Map.entry("蒜", 5.0), Map.entry("姜", 10.0), Map.entry("葱", 10.0), Map.entry("辣椒", 10.0),
            Map.entry("八角", 1.0), Map.entry("香叶", 0.5), Map.entry("番茄", 150.0), Map.entry("西红柿", 150.0),
            Map.entry("土豆", 200.0), Map.entry("马铃薯", 200.0), Map.entry("洋葱", 200.0), Map.entry("茄子", 250.0),
            Map.entry("黄瓜", 200.0), Map.entry("胡萝卜", 150.0), Map.entry("青椒", 100.0), Map.entry("南瓜", 800.0),
            Map.entry("鸡翅", 50.0), Map.entry("鸡腿", 150.0), Map.entry("豆腐", 300.0), Map.entry("鲈鱼", 500.0),
            Map.entry("鱼", 500.0), Map.entry("虾", 20.0), Map.entry("香菇", 20.0), Map.entry("柠檬", 100.0));

    private static final Map<String, Double> PIECE_BY_CATEGORY = Map.ofEntries(
            Map.entry("蔬菜", 150.0), Map.entry("水果", 150.0), Map.entry("畜禽肉", 200.0), Map.entry("水产海鲜", 300.0),
            Map.entry("蛋类", 50.0), Map.entry("豆制品", 200.0), Map.entry("乳制品", 250.0), Map.entry("谷物主食", 100.0),
            Map.entry("调味料", 5.0), Map.entry("干菌藻", 10.0), Map.entry("坚果", 10.0), Map.entry("速冻", 200.0));

    private PortionEstimator() {
    }

    public static double grams(RecipeLine line) {
        String unit = line.unitName() == null ? "" : line.unitName().trim();
        double amount = line.amount() == null || line.amount().signum() <= 0 ? 1 : line.amount().doubleValue();
        double g;
        if (WEIGHT_UNITS.containsKey(unit)) {
            g = amount * WEIGHT_UNITS.get(unit);
        } else if (SPOON_UNITS.containsKey(unit)) {
            g = amount * SPOON_UNITS.get(unit);
        } else if (unit.contains("适量") || unit.contains("少许") || unit.contains("若干")
                || (unit.isEmpty() && (line.amount() == null || line.amount().signum() <= 0))) {
            g = vague(line);
        } else if (unit.isEmpty()) { // 只有数字：≥20 视为克数，否则视为件数
            g = amount >= PIECE_OR_GRAM_THRESHOLD ? amount : amount * pieceGrams(line);
        } else {
            g = amount * pieceGrams(line);
        }
        return Math.min(Math.max(g, 0), MAX_GRAMS);
    }

    static double vague(RecipeLine line) {
        String n = line.name() == null ? "" : line.name();
        if (n.contains("盐")) return 2;
        if (n.contains("抽") || n.contains("酱油") || n.contains("蚝油")) return 10;
        if (n.contains("油")) return 10;
        if ("调味料".equals(line.categoryName()) || n.contains("酱") || n.contains("醋") || n.contains("糖")) return 5;
        return 20;
    }

    static double pieceGrams(RecipeLine line) {
        String n = line.name() == null ? "" : line.name();
        for (Map.Entry<String, Double> e : PIECE_BY_NAME) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        if (line.categoryName() != null && PIECE_BY_CATEGORY.containsKey(line.categoryName())) {
            return PIECE_BY_CATEGORY.get(line.categoryName());
        }
        return 100;
    }
}
