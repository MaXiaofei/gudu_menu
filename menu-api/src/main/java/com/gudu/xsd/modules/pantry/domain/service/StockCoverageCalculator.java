package com.gudu.xsd.modules.pantry.domain.service;

import com.gudu.xsd.modules.pantry.domain.model.Coverage;
import com.gudu.xsd.modules.pantry.domain.model.DishMaterial;
import com.gudu.xsd.modules.pantry.domain.model.MissingIngredient;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 领域服务（纯函数）：菜谱用料 × 本家余量 → {@link Coverage}。
 * 规则：调料不计；同一食材只计一次；档位 ENOUGH/LOW 计「够」，NONE 或未建档计「缺」。
 */
public final class StockCoverageCalculator {

    public Coverage calculate(Collection<DishMaterial> materials, Map<Long, StockLevel> levels) {
        if (materials == null || materials.isEmpty()) return Coverage.empty();
        Set<Long> seen = new LinkedHashSet<>();
        int total = 0;
        int enough = 0;
        List<MissingIngredient> missing = new ArrayList<>();
        for (DishMaterial m : materials) {
            if (m == null || m.ingredientId() == null || m.condiment() || !seen.add(m.ingredientId())) continue;
            total++;
            StockLevel level = levels == null ? null : levels.get(m.ingredientId());
            if (level != null && level.isAvailable()) {
                enough++;
            } else {
                missing.add(new MissingIngredient(m.ingredientId(), m.name()));
            }
        }
        return new Coverage(enough, total, missing);
    }

    /** 「家里够的优先」：enough/total 降序，其次 missing 数升序。 */
    public static Comparator<Coverage> bestFirst() {
        return Comparator.comparingDouble(Coverage::ratio).reversed()
                .thenComparingInt(c -> c.missing().size());
    }
}
