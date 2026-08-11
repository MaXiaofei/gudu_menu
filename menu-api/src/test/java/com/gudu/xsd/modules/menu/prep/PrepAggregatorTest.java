package com.gudu.xsd.modules.menu.prep;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrepAggregator 纯函数单测。参照 {@code ShoppingAggregatorTest} 范式。
 * 覆盖：单菜单料 / 多菜共用 / 份数缩放 / 同菜同料去重 / 空输入。
 *
 * <p>V55（食材去单位）：不再按克汇总，聚合输出每道菜用量原文（amount + unitName）。
 */
class PrepAggregatorTest {

    private final PrepAggregator agg = new PrepAggregator();

    /** Usage 构造顺序：(dishId, servingFactor, ingredientId, amount, unitName)。 */
    private PrepAggregator.Usage u(Long dishId, Long ingId, String amount, String factor, String unitName) {
        return new PrepAggregator.Usage(dishId, new BigDecimal(factor), ingId, new BigDecimal(amount), unitName);
    }

    /** 1 道菜 1 样料：聚合出 1 行，dishCount=1，用量原文保留。 */
    @Test
    void aggregate_单菜单料_一行_dishCount为1() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(u(10L, 1L, "300", "1", "g")));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).ingredientId()).isEqualTo(1L);
        assertThat(lines.get(0).dishCount()).isEqualTo(1);
        assertThat(lines.get(0).dishIds()).containsExactly(10L);
        assertThat(lines.get(0).usages()).hasSize(1);
        assertThat(lines.get(0).usages().get(0).amount()).isEqualByComparingTo("300");
        assertThat(lines.get(0).usages().get(0).unitName()).isEqualTo("g");
    }

    /** 2 道菜共用 1 样料：两笔用量原文都保留、dishCount=2、dishIds 含两菜。 */
    @Test
    void aggregate_多菜共用料_明细保留且dishCount为2() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(
                u(10L, 1L, "100", "1", "g"),
                u(20L, 1L, "2", "1", "个")));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).ingredientId()).isEqualTo(1L);
        assertThat(lines.get(0).dishCount()).isEqualTo(2);
        assertThat(lines.get(0).dishIds()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(lines.get(0).usages()).hasSize(2);
        assertThat(lines.get(0).usages()).extracting(PrepAggregator.UsageText::unitName)
                .containsExactlyInAnyOrder("g", "个");
    }

    /** servingFactor=2：用量原文保留，份数随 UsageText 携带（前端展示 ×2）。 */
    @Test
    void aggregate_份数缩放_servingFactor随明细携带() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(u(10L, 1L, "150", "2", "g")));

        assertThat(lines.get(0).usages().get(0).servingFactor()).isEqualByComparingTo("2");
    }

    /** 同菜同料多行（防御）：明细都保留，dishCount 仍 1（dishId 去重）。 */
    @Test
    void aggregate_同菜同料多行_明细保留且dishId去重() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(List.of(
                u(10L, 1L, "100", "1", "g"),
                u(10L, 1L, "50", "1", "g")));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).usages()).hasSize(2);
        assertThat(lines.get(0).dishCount()).isEqualTo(1);
        assertThat(lines.get(0).dishIds()).containsExactly(10L);
    }

    /** null/空 输入：返回空列表（不 NPE）。 */
    @Test
    void aggregate_空输入_返回空列表() {
        assertThat(agg.aggregate(null)).isEmpty();
        assertThat(agg.aggregate(List.of())).isEmpty();
    }

    /** servingFactor null：兜底按 1（与 MenuService.summary 一致）。 */
    @Test
    void aggregate_servingFactor为null_兜底按1() {
        List<PrepAggregator.PrepLine> lines = agg.aggregate(
                List.of(new PrepAggregator.Usage(10L, null, 1L, new BigDecimal("200"), "g")));

        assertThat(lines.get(0).usages().get(0).servingFactor()).isEqualByComparingTo("1");
    }
}
