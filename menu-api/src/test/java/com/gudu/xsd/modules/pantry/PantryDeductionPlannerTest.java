package com.gudu.xsd.modules.pantry;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FIFO 扣减规划纯函数测试。调用方负责批次排序，这里只测算法。 */
class PantryDeductionPlannerTest {

    private final PantryDeductionPlanner planner = new PantryDeductionPlanner();

    private Pantry batch(long id, String grams, String amount) {
        Pantry p = new Pantry();
        p.setId(id);
        p.setGrams(new BigDecimal(grams));
        p.setAmount(new BigDecimal(amount));
        return p;
    }

    @Test
    void 单批次够_扣部分_剩余量_金额按比例缩() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "100", "2")), new BigDecimal("30"));
        assertThat(plan.ops()).hasSize(1);
        assertThat(plan.ops().get(0).deductGrams()).isEqualByComparingTo("30");
        assertThat(plan.ops().get(0).remainGrams()).isEqualByComparingTo("70");
        assertThat(plan.ops().get(0).newAmount()).isEqualByComparingTo("1.40"); // 2 × 70/100
        assertThat(plan.shortageGrams()).isEqualByComparingTo("0");
    }

    @Test
    void 单批次不够_全扣完_记欠量() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "30", "1")), new BigDecimal("100"));
        assertThat(plan.ops()).hasSize(1);
        assertThat(plan.ops().get(0).deductGrams()).isEqualByComparingTo("30");
        assertThat(plan.ops().get(0).remainGrams()).isEqualByComparingTo("0");
        assertThat(plan.ops().get(0).newAmount()).isEqualByComparingTo("0.00");
        assertThat(plan.shortageGrams()).isEqualByComparingTo("70");
    }

    @Test
    void 多批次_FIFO_先扣完早的再扣下一批() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "50", "1"), batch(2, "60", "2")),
                new BigDecimal("100"));
        assertThat(plan.ops()).hasSize(2);
        assertThat(plan.ops().get(0).pantryId()).isEqualTo(1L);    // 第一批先扣完
        assertThat(plan.ops().get(0).deductGrams()).isEqualByComparingTo("50");
        assertThat(plan.ops().get(0).remainGrams()).isEqualByComparingTo("0");
        assertThat(plan.ops().get(1).pantryId()).isEqualTo(2L);
        assertThat(plan.ops().get(1).deductGrams()).isEqualByComparingTo("50");
        assertThat(plan.ops().get(1).remainGrams()).isEqualByComparingTo("10");
        assertThat(plan.shortageGrams()).isEqualByComparingTo("0");
    }

    @Test
    void 需求为零_空操作() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "100", "2")), BigDecimal.ZERO);
        assertThat(plan.ops()).isEmpty();
        assertThat(plan.shortageGrams()).isEqualByComparingTo("0");
    }

    @Test
    void 没有批次_全记欠() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(), new BigDecimal("80"));
        assertThat(plan.ops()).isEmpty();
        assertThat(plan.shortageGrams()).isEqualByComparingTo("80");
    }
}
