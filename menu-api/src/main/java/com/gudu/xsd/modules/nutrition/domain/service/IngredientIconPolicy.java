package com.gudu.xsd.modules.nutrition.domain.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 食材插画 key 规则（纯函数，prototype-ui-rollout P0）。
 *
 * <p>单一来源是 {@code docs/design/ingredient-icons.json}；本类与 V68 回填 SQL 均由
 * {@code IngredientIconPolicyTest} 校验与之逐项一致，改规则先改 JSON 再同步这里。
 *
 * <p>规则：名称包含关键词即命中，取最长关键词，等长取靠前一项；未命中按采购品类名兜底；仍无返回 null。
 */
public final class IngredientIconPolicy {

    /** 一个插画 key 及其关键词（顺序即优先级）。 */
    public record Rule(String key, List<String> keywords) {
        Rule(String key, String... keywords) {
            this(key, List.of(keywords));
        }
    }

    public static final List<Rule> RULES = List.of(
            new Rule("tomato", "番茄", "西红柿", "圣女果"),
            new Rule("egg", "鸡蛋", "鸭蛋", "鹌鹑蛋", "蛋"),
            new Rule("scallion", "葱", "香葱", "小葱", "韭菜", "蒜苗"),
            new Rule("potato", "土豆", "马铃薯", "红薯", "山药", "芋头"),
            new Rule("broccoli", "西兰花", "西蓝花", "菜花", "花菜"),
            new Rule("fish", "鱼", "鲈鱼", "鲫鱼", "带鱼", "三文鱼", "黑鱼"),
            new Rule("spinach", "菠菜", "油麦菜", "空心菜", "芹菜", "生菜"),
            new Rule("tofu", "豆腐", "香干", "腐竹", "豆皮"),
            new Rule("pork", "五花肉", "猪肉", "肉末", "里脊", "培根", "火腿", "腊肠"),
            new Rule("milk", "牛奶", "酸奶", "奶油", "淡奶", "奶粉"),
            new Rule("ginger", "姜"),
            new Rule("garlic", "蒜", "大蒜", "蒜瓣"),
            new Rule("shrimp", "虾", "虾仁", "大虾", "龙虾"),
            new Rule("wing", "鸡翅", "翅中", "翅根", "鸡腿", "鸡肉", "鸡胸", "鸡"),
            new Rule("ribs", "排骨", "肋排", "猪蹄", "棒骨", "羊排"),
            new Rule("lotus", "莲藕", "藕"),
            new Rule("pumpkin", "南瓜"),
            new Rule("rice", "大米", "米饭", "小米", "糯米", "米", "面粉", "燕麦"),
            new Rule("sauce", "酱油", "生抽", "老抽", "豉油", "醋", "料酒", "蚝油", "酱", "盐", "糖", "鸡精", "味精", "胡椒"),
            new Rule("oil", "油", "食用油", "香油", "麻油", "橄榄油"),
            new Rule("carrot", "胡萝卜", "萝卜"),
            new Rule("pepper", "青椒", "辣椒", "彩椒", "尖椒"),
            new Rule("mushroom", "蘑菇", "香菇", "金针菇", "杏鲍菇", "木耳", "平菇"),
            new Rule("cabbage", "白菜", "包菜", "卷心菜", "娃娃菜", "小白菜", "青菜"),
            new Rule("noodle", "面条", "挂面", "意面", "粉丝", "米粉", "年糕"),
            new Rule("crab", "蟹", "螃蟹", "梭子蟹"),
            new Rule("beef", "牛肉", "牛腩", "肥牛", "羊肉"),
            new Rule("cucumber", "黄瓜", "丝瓜", "苦瓜", "西葫芦", "冬瓜"),
            new Rule("tissue", "纸巾", "抽纸", "保鲜膜", "洗洁精")
    );

    public static final Map<String, String> CATEGORY_FALLBACK;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("蔬菜", "cabbage");
        m.put("水果", "tomato");
        m.put("畜禽肉", "pork");
        m.put("水产海鲜", "fish");
        m.put("蛋类", "egg");
        m.put("乳制品", "milk");
        m.put("调味料", "sauce");
        m.put("谷物主食", "rice");
        m.put("坚果", "rice");
        m.put("干菌藻", "mushroom");
        m.put("速冻", "noodle");
        m.put("饮料", "milk");
        m.put("肉禽蛋", "pork");
        m.put("肉类", "pork");
        m.put("禽蛋", "egg");
        m.put("水产", "fish");
        m.put("调料", "sauce");
        m.put("调味品", "sauce");
        m.put("粮油", "rice");
        m.put("主食", "rice");
        m.put("米面粮油", "rice");
        m.put("乳品", "milk");
        m.put("奶制品", "milk");
        m.put("豆制品", "tofu");
        m.put("菌菇", "mushroom");
        m.put("日用", "tissue");
        m.put("其他", "tissue");
        CATEGORY_FALLBACK = Collections.unmodifiableMap(m);
    }

    private IngredientIconPolicy() {
    }

    /**
     * @param name         食材名（可空）
     * @param categoryName 采购品类名（可空）
     * @return 插画 key；无法判定返回 null（客户端退首字）
     */
    public static String resolve(String name, String categoryName) {
        String byName = byName(name);
        if (byName != null) return byName;
        return categoryName == null ? null : CATEGORY_FALLBACK.get(categoryName.trim());
    }

    /** 仅按名称关键词匹配。 */
    public static String byName(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        String best = null;
        int bestLen = 0;
        for (Rule r : RULES) {
            for (String w : r.keywords()) {
                if (w.length() > bestLen && n.contains(w)) {
                    best = r.key();
                    bestLen = w.length();
                }
            }
        }
        return best;
    }
}
