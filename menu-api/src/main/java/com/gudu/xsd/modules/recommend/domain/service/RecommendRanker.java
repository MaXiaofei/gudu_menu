package com.gudu.xsd.modules.recommend.domain.service;

import com.gudu.xsd.modules.recommend.domain.model.DishScore;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** 首页智荐排序（纯函数）。 */
public final class RecommendRanker {

    /** 覆盖率降序 → 缺得少优先 → 做过多优先 → id 升序（稳定）。 */
    static final Comparator<DishScore> BY_COVERAGE = Comparator
            .comparingDouble(DishScore::coverageRatio).reversed()
            .thenComparingInt(DishScore::missingCount)
            .thenComparing(Comparator.comparingInt(DishScore::cookedCount).reversed())
            .thenComparing(DishScore::dishId);

    public List<Long> topByCoverage(Collection<DishScore> candidates, Set<Long> exclude, int limit) {
        if (candidates == null || limit <= 0) return List.of();
        return candidates.stream()
                .filter(s -> s.dishId() != null && (exclude == null || !exclude.contains(s.dishId())))
                .sorted(BY_COVERAGE)
                .limit(limit)
                .map(DishScore::dishId)
                .distinct()
                .toList();
    }
}
