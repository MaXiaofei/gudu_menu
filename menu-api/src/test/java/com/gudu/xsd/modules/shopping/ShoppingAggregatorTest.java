package com.gudu.xsd.modules.shopping;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购清单聚合算法纯函数测试（核心算法 TDD）。
 * 参照 MenuCalcService / PantryService 范式：不依赖 Spring，直接 new ShoppingAggregator()。
 *
 * <p>V55（食材去单位）算法契约：
 *  - 按 ingredient_id 去重（采购单只回答「哪些食材需要买」，用量原文展示在备菜 Tab）；
 *  - referenceGrams 停用，不再合计克数；
 *  - 采购品类(purchaseCategoryId) 随行携带（取首条，正常同食材品类一致），仅用于参考分区。
 */
class ShoppingAggregatorTest {

    private final ShoppingAggregator agg = new ShoppingAggregator();

    @Test
    void 同食材跨多菜_去重为一行() {
        // 菜A 番茄 + 菜B 番茄 + 鸡蛋
        var usages = List.of(
                new ShoppingAggregator.Usage(1L, 24L),   // 番茄
                new ShoppingAggregator.Usage(1L, 24L),   // 番茄（同食材合并）
                new ShoppingAggregator.Usage(2L, 27L));  // 鸡蛋
        var lines = agg.aggregate(usages);
        assertThat(lines).hasSize(2);  // 番茄、鸡蛋
        var tomato = lines.stream().filter(l -> l.ingredientId() == 1L).findFirst().get();
        assertThat(tomato.purchaseCategoryId()).isEqualTo(24L);
        var egg = lines.stream().filter(l -> l.ingredientId() == 2L).findFirst().get();
        assertThat(egg.purchaseCategoryId()).isEqualTo(27L);
    }

    @Test
    void 同食材不同菜不同用量_聚合为一行() {
        // 即使来自不同菜，同 ingredient_id 也合并为一行
        var usages = List.of(
                new ShoppingAggregator.Usage(1L, 24L),
                new ShoppingAggregator.Usage(1L, 24L),
                new ShoppingAggregator.Usage(1L, 24L));
        var lines = agg.aggregate(usages);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).ingredientId()).isEqualTo(1L);
    }

    @Test
    void 品类随行携带_取首条() {
        var usages = List.of(
                new ShoppingAggregator.Usage(1L, 24L),   // 蔬菜
                new ShoppingAggregator.Usage(2L, 27L));  // 蛋类
        var lines = agg.aggregate(usages);
        var tomato = lines.stream().filter(l -> l.ingredientId() == 1L).findFirst().get();
        assertThat(tomato.purchaseCategoryId()).isEqualTo(24L);
        var egg = lines.stream().filter(l -> l.ingredientId() == 2L).findFirst().get();
        assertThat(egg.purchaseCategoryId()).isEqualTo(27L);
    }

    @Test
    void 空用量_返回空列表() {
        assertThat(agg.aggregate(List.of())).isEmpty();
        assertThat(agg.aggregate(null)).isEmpty();
    }

    @Test
    void 首条品类为空_用后续行品类补齐() {
        var usages = List.of(
                new ShoppingAggregator.Usage(1L, null),
                new ShoppingAggregator.Usage(1L, 24L));
        var lines = agg.aggregate(usages);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).purchaseCategoryId()).isEqualTo(24L);
    }

    @Test
    void 按采购品类分区() {
        var usages = List.of(
                new ShoppingAggregator.Usage(1L, 24L),   // 蔬菜
                new ShoppingAggregator.Usage(1L, 24L),   // 蔬菜（合并）
                new ShoppingAggregator.Usage(2L, 27L));  // 蛋类
        var grouped = agg.groupByCategory(usages);
        assertThat(grouped.keySet()).containsExactlyInAnyOrder(24L, 27L);
        assertThat(grouped.get(24L)).hasSize(1);  // 番茄合并后 1 行
        assertThat(grouped.get(27L)).hasSize(1);
    }
}
