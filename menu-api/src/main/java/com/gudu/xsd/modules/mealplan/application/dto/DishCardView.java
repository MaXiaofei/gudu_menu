package com.gudu.xsd.modules.mealplan.application.dto;

import java.util.List;

/**
 * 契约 DishCard（+ sodiumMg：1 人份钠估算，换一道备选与营养报告展示用）。
 *
 * @param difficulty 简单 / 中等 / 较难（菜谱难度 1–5 折算）
 */
public record DishCardView(long id, String name, String coverUrl, Integer minutes, String difficulty,
                           Coverage coverage, List<String> tags, Integer sodiumMg) {

    public record Coverage(int have, int total, List<String> missing) {
    }

    public static String difficultyLabel(Integer level) {
        if (level == null) return null;
        if (level <= 2) return "简单";
        return level == 3 ? "中等" : "较难";
    }
}
