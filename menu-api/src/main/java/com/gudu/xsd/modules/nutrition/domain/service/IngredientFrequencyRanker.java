package com.gudu.xsd.modules.nutrition.domain.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 常用食材排序（纯函数）：score = 做过的菜里出现次数 × 2 + 菜谱库用料出现次数；
 * 同分按 id 升序（稳定），只保留 score > 0。
 */
public final class IngredientFrequencyRanker {

    static final int COOKED_WEIGHT = 2;

    public List<Long> rank(Map<Long, Integer> cookedUsage, Map<Long, Integer> recipeUsage, int limit) {
        if (limit <= 0) return List.of();
        Map<Long, Integer> score = new HashMap<>();
        if (cookedUsage != null) cookedUsage.forEach((id, c) -> merge(score, id, c == null ? 0 : c * COOKED_WEIGHT));
        if (recipeUsage != null) recipeUsage.forEach((id, c) -> merge(score, id, c == null ? 0 : c));
        if (score.isEmpty()) return Collections.emptyList();
        return score.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static void merge(Map<Long, Integer> score, Long id, int add) {
        if (id != null) score.merge(id, add, Integer::sum);
    }
}
