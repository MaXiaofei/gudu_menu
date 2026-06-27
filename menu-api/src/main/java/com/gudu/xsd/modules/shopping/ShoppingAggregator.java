package com.gudu.xsd.modules.shopping;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购清单聚合算法（纯函数，算法地基）。
 *
 * <p>不依赖任何 Mapper / Spring 状态，可单测。参照 {@code MenuCalcService} 范式。
 *
 * <p>新设计（redesign）聚合规则：
 * <ul>
 *   <li>按 ingredientId 去重，同食材的多笔用量合计克数 → referenceGrams（参考克数，信息性提示）；</li>
 *   <li>不再按 unit 合并 —— 采购单位（斤/把/个 等）由用户在前端填，不预合并；</li>
 *   <li>purchaseCategoryId 随行携带（同食材内取首条，正常同食材品类一致），仅用于参考分区。</li>
 * </ul>
 */
@Component
public class ShoppingAggregator {

    /** 一笔用量：某食材用掉多少克（来自某道菜的 dish_ingredient，已 join ingredient 拿采购品类）。 */
    public record Usage(Long ingredientId, BigDecimal amount, Long purchaseCategoryId) {}

    /** 聚合后的一行采购明细：食材 + 参考克数 + 采购品类（采购量/单位由用户后填）。 */
    public record ShoppingLine(Long ingredientId, BigDecimal referenceGrams, Long purchaseCategoryId) {}

    /**
     * 聚合：把多笔用量按 ingredientId 去重，克数相加 → referenceGrams。
     *
     * @param usages 用量列表，可空
     * @return 聚合后的采购行（按首次出现顺序）
     */
    public List<ShoppingLine> aggregate(List<Usage> usages) {
        if (usages == null || usages.isEmpty()) return new ArrayList<>();
        Map<Long, ShoppingLine> acc = new LinkedHashMap<>();
        for (Usage u : usages) {
            if (u == null || u.ingredientId() == null) continue;
            BigDecimal amt = u.amount() == null ? BigDecimal.ZERO : u.amount();
            ShoppingLine cur = acc.get(u.ingredientId());
            if (cur == null) {
                acc.put(u.ingredientId(), new ShoppingLine(u.ingredientId(), amt, u.purchaseCategoryId()));
            } else {
                acc.put(u.ingredientId(), new ShoppingLine(
                        cur.ingredientId(),
                        cur.referenceGrams().add(amt),
                        cur.purchaseCategoryId() != null ? cur.purchaseCategoryId() : u.purchaseCategoryId()));
            }
        }
        return new ArrayList<>(acc.values());
    }

    /**
     * 按采购品类(purchaseCategoryId)分区：先 aggregate 聚合，再按品类分组。
     *
     * @return categoryId -> 该品类下的采购行列表（null 品类归到 key=null）
     */
    public Map<Long, List<ShoppingLine>> groupByCategory(List<Usage> usages) {
        List<ShoppingLine> lines = aggregate(usages);
        Map<Long, List<ShoppingLine>> grouped = new LinkedHashMap<>();
        for (ShoppingLine l : lines) {
            Long cat = l.purchaseCategoryId();  // null 品类归到 key=null 一组
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(l);
        }
        return grouped;
    }
}
