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
 * <p>按 ingredientId 聚合食集各菜用料（不减库存），统计共用计数。
 * 参照 {@link com.gudu.xsd.modules.shopping.ShoppingAggregator} 范式：不依赖 Mapper/Spring 状态，可单测。
 *
 * <p>聚合规则（V55 食材去单位后，不再按克汇总）：
 * <ul>
 *   <li>按 ingredientId 去重，保留每道菜的用量原文（amount + unitName，如"2个"）→ 前端展示明细；</li>
 *   <li>dishIds = 用到该食材的菜集合（去重），dishCount = dishIds.size()
 *       （≥2 即共用项，前端 🔥 高亮）；</li>
 *   <li>不减库存（备菜只管"备多少"，采购才管"够不够"）。</li>
 * </ul>
 */
@Component
public class PrepAggregator {

    /** 一笔用量：某食材在某道菜里的用量原文（amount + 单位名；servingFactor 供前端显示 ×N）。 */
    public record Usage(Long dishId, BigDecimal servingFactor, Long ingredientId,
                        BigDecimal amount, String unitName) {}

    /** 一笔用量原文：菜 id + 数字 + 单位名 + 份数（>1 时展示 ×N）。 */
    public record UsageText(Long dishId, BigDecimal amount, String unitName, BigDecimal servingFactor) {}

    /** 聚合后一行：食材 + 用量原文明细 + 被几道菜用 + dishId 列表。 */
    public record PrepLine(Long ingredientId, List<UsageText> usages, int dishCount, List<Long> dishIds) {}

    /**
     * 聚合：按 ingredientId 合并用量原文，统计共用计数。
     *
     * @param usages 用量列表，可空
     * @return 聚合行（按首次出现顺序）
     */
    public List<PrepLine> aggregate(List<Usage> usages) {
        if (usages == null || usages.isEmpty()) return new ArrayList<>();
        Map<Long, List<UsageText>> usagesAcc = new LinkedHashMap<>();
        Map<Long, Set<Long>> dishIdsAcc = new LinkedHashMap<>();
        for (Usage u : usages) {
            if (u == null || u.ingredientId() == null) continue;
            BigDecimal factor = u.servingFactor() == null ? BigDecimal.ONE : u.servingFactor();
            usagesAcc.computeIfAbsent(u.ingredientId(), k -> new ArrayList<>())
                    .add(new UsageText(u.dishId(), u.amount(), u.unitName(), factor));
            if (u.dishId() != null) {
                dishIdsAcc.computeIfAbsent(u.ingredientId(), k -> new LinkedHashSet<>()).add(u.dishId());
            }
        }
        List<PrepLine> lines = new ArrayList<>();
        for (Long ingId : usagesAcc.keySet()) {
            List<Long> dishIds = new ArrayList<>(dishIdsAcc.getOrDefault(ingId, new LinkedHashSet<>()));
            lines.add(new PrepLine(ingId, usagesAcc.get(ingId), dishIds.size(), dishIds));
        }
        return lines;
    }
}
