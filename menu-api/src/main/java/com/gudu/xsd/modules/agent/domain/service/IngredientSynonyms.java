package com.gudu.xsd.modules.agent.domain.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检索用的食材 / 菜名同义词归一（方言、异写 → 菜谱库里的标准写法）。
 * 只做检索词替换，不改用户原话；评测 recipe_search.synonym、robustness.dialect 覆盖。
 */
public final class IngredientSynonyms {

    /** 别名 → 标准名（按插入顺序替换，长词在前避免部分替换）。 */
    private static final Map<String, String> ALIAS = new LinkedHashMap<>();

    static {
        ALIAS.put("西蓝花", "西兰花");
        ALIAS.put("西红柿", "番茄");
        ALIAS.put("马铃薯", "土豆");
        ALIAS.put("洋芋", "土豆");
        ALIAS.put("地蛋", "土豆");
        ALIAS.put("豆付", "豆腐");
        ALIAS.put("卷心菜", "包菜");
        ALIAS.put("圆白菜", "包菜");
        ALIAS.put("番薯", "红薯");
        ALIAS.put("地瓜", "红薯");
    }

    private IngredientSynonyms() {
    }

    public static String canonical(String s) {
        if (s == null || s.isBlank()) return s;
        String out = s;
        for (Map.Entry<String, String> e : ALIAS.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }
}
