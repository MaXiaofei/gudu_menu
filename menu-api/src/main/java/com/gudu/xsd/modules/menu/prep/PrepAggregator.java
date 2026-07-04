package com.gudu.xsd.modules.menu.prep;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 备菜聚合算法（纯函数，算法地基）。
 *
 * <p>按 ingredientId 聚合食集各菜用料（×servingFactor，不减库存），统计共用计数。
 * 参照 {@link com.gudu.xsd.modules.shopping.ShoppingAggregator} 范式：不依赖 Mapper/Spring 状态，可单测。
 *
 * <p>聚合规则：
 * <ul>
 *   <li>按 ingredientId 去重，同食材多笔用量合计克数（×servingFactor）→ totalGrams；</li>
 *   <li>dishIds = 用到该食材的菜集合（去重），dishCount = dishIds.size()
 *       （≥2 即共用项，前端 🔥 高亮）；</li>
 *   <li>不减库存（备菜只管"备多少"，采购才管"够不够"）。</li>
 * </ul>
 */
@Component
public class PrepAggregator {

    /** 一笔用量：某食材在某道菜里用多少克（×servingFactor 前的原始 grams）。 */
    public record Usage(Long dishId, BigDecimal servingFactor, Long ingredientId, BigDecimal grams) {}

    /** 聚合后一行：食材 + 总克数（已 ×servingFactor）+ 被几道菜用 + dishId 列表。 */
    public record PrepLine(Long ingredientId, BigDecimal totalGrams, int dishCount, List<Long> dishIds) {}

    /**
     * 聚合：按 ingredientId 合并用量（×servingFactor），统计共用计数。
     *
     * @param usages 用量列表，可空
     * @return 聚合行（按首次出现顺序）
     */
    public List<PrepLine> aggregate(List<Usage> usages) {
        if (usages == null || usages.isEmpty()) return new ArrayList<>();
        Map<Long, BigDecimal> gramsAcc = new LinkedHashMap<>();
        Map<Long, Set<Long>> dishIdsAcc = new LinkedHashMap<>();
        for (Usage u : usages) {
            if (u == null || u.ingredientId() == null) continue;
            BigDecimal factor = u.servingFactor() == null ? BigDecimal.ONE : u.servingFactor();
            BigDecimal grams = u.grams() == null ? BigDecimal.ZERO : u.grams();
            gramsAcc.merge(u.ingredientId(), grams.multiply(factor), BigDecimal::add);
            if (u.dishId() != null) {
                dishIdsAcc.computeIfAbsent(u.ingredientId(), k -> new LinkedHashSet<>()).add(u.dishId());
            }
        }
        List<PrepLine> lines = new ArrayList<>();
        for (Long ingId : gramsAcc.keySet()) {
            List<Long> dishIds = new ArrayList<>(dishIdsAcc.getOrDefault(ingId, new LinkedHashSet<>()));
            lines.add(new PrepLine(ingId, gramsAcc.get(ingId), dishIds.size(), dishIds));
        }
        return lines;
    }
}
