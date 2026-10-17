package com.gudu.xsd.modules.mealplan.domain.model;

/** 营养报告五项（key 与契约一致）。LIMIT = 上限型（超了提醒），GOAL = 目标型（达到算好）。 */
public enum NutrientKey {
    ENERGY("energy", "能量", "kcal", false),
    PROTEIN("protein", "蛋白质", "g", false),
    FAT("fat", "脂肪", "g", true),
    SODIUM("sodium", "钠", "mg", true),
    FIBER("fiber", "膳食纤维", "g", false);

    private final String key;
    private final String label;
    private final String unit;
    private final boolean limit;

    NutrientKey(String key, String label, String unit, boolean limit) {
        this.key = key;
        this.label = label;
        this.unit = unit;
        this.limit = limit;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    /** 上限型指标（脂肪、钠）。 */
    public boolean isLimit() {
        return limit;
    }
}
