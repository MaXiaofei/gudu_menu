package com.gudu.xsd.modules.menu.prep;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrepAggregator 纯函数单测。参照 {@code ShoppingAggregatorTest} 范式。
 * 覆盖：单菜单料 / 多菜共用 / 份数缩放 / 同菜同料去重 / 空输入。
 */
class PrepAggregatorTest {

    private final PrepAggregator agg = new PrepAggregator();

    /** Usage 构造顺序：(dishId, servingFactor, ingredientId, grams)。 */
    private PrepAggregator.Usage u(Long dishId, Long ingId, String grams, String factor) {
        return new PrepAggregator.Usage(dishId, new BigDecimal(factor), ingId, new BigDecimal(grams));
    }

    /** 1 道菜 1 样料：聚合出 1 行，dishCount=1。 */
    @Test
    void aggregate_单菜单料_一行_dishCount为1() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(u(10L, 1L, "300", "1")));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).ingredientId()).isEqualTo(1L);
        assertThat(lines.get(0).totalGrams()).isEqualByComparingTo("300");
        assertThat(lines.get(0).dishCount()).isEqualTo(1);
        assertThat(lines.get(0).dishIds()).containsExactly(10L);
    }

    /** 2 道菜共用 1 样料：合并 grams、dishCount=2、dishIds 含两菜。 */
    @Test
    void aggregate_多菜共用料_合并grams且dishCount为2() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(
                u(10L, 1L, "100", "1"),
                u(20L, 1L, "200", "1")));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).ingredientId()).isEqualTo(1L);
        assertThat(lines.get(0).totalGrams()).isEqualByComparingTo("300");
        assertThat(lines.get(0).dishCount()).isEqualTo(2);
        assertThat(lines.get(0).dishIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    /** servingFactor=2：用量翻倍。 */
    @Test
    void aggregate_份数缩放_grams按servingFactor翻倍() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(u(10L, 1L, "150", "2")));

        assertThat(lines.get(0).totalGrams()).isEqualByComparingTo("300");
    }

    /** 同菜同料多行（防御）：累加 grams，dishCount 仍 1（dishId 去重）。 */
    @Test
    void aggregate_同菜同料多行_累加且dishId去重() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(
                u(10L, 1L, "100", "1"),
                u(10L, 1L, "50", "1")));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).totalGrams()).isEqualByComparingTo("150");
        assertThat(lines.get(0).dishCount()).isEqualTo(1);
        assertThat(lines.get(0).dishIds()).containsExactly(10L);
    }

    /** null/空 输入：返回空列表（不 NPE）。 */
    @Test
    void aggregate_空输入_返回空列表() {
        assertThat(agg.aggregate(null)).isEmpty();
        assertThat(agg.aggregate(List.of())).isEmpty();
    }

    /** servingFactor null：兜底按 1 算（与 MenuService.summary 一致）。 */
    @Test
    void aggregate_servingFactor为null_兜底按1() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(
                List.of(new PrepAggregator.Usage(10L, null, 1L, new BigDecimal("200"))));

        assertThat(lines.get(0).totalGrams()).isEqualByComparingTo("200");
    }
}
