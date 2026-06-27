package com.gudu.xsd.modules.dailylog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 每日饮食记录的营养聚合纯函数测试：把一组摄入项(菜品/食材)聚合成总营养(metricId→value)。
 *
 * 算法（复用 NutritionCalcService 思想，独立成纯函数便于可测）：
 *  - ingredient 项：valuePerUnit 是 per100g，qty 是克 → value × qty / 100
 *  - dish 项：valuePerUnit 是该菜 per份营养(已聚合)，qty 是份数 → value × qty
 */
class DailyLogCalcServiceTest {

    private final DailyLogCalcService calc = new DailyLogCalcService();

    @Test
    void 汇总各摄入项的总营养() {
        // 两项 ingredient 摄入：番茄 200g(calorie 19/100g) + 鸡蛋 100g(calorie 144/100g)
        var items = List.of(
                new DailyLogCalcService.Intake(1L, false, new BigDecimal("19"), new BigDecimal("200")),
                new DailyLogCalcService.Intake(1L, false, new BigDecimal("144"), new BigDecimal("100")));
        Map<Long, BigDecimal> r = calc.aggregateIntake(items);
        // calorie: 19*200/100 + 144*100/100 = 38 + 144 = 182
        assertThat(r.get(1L)).isEqualByComparingTo("182");
    }

    @Test
    void 菜品摄入按份数缩放() {
        // 一道菜 calorie=300(已聚合的 per份)，2 份
        var items = List.of(new DailyLogCalcService.Intake(1L, true, new BigDecimal("300"), new BigDecimal("2")));
        assertThat(calc.aggregateIntake(items).get(1L)).isEqualByComparingTo("600");
    }

    @Test
    void 多指标_菜品与食材混合() {
        // 菜品 1 份：calorie 300、protein 20；食材(鸡蛋) 100g：calorie 144、protein 13
        var items = List.of(
                new DailyLogCalcService.Intake(1L, true, new BigDecimal("300"), BigDecimal.ONE),
                new DailyLogCalcService.Intake(2L, true, new BigDecimal("20"), BigDecimal.ONE),
                new DailyLogCalcService.Intake(1L, false, new BigDecimal("144"), new BigDecimal("100")),
                new DailyLogCalcService.Intake(2L, false, new BigDecimal("13"), new BigDecimal("100")));
        Map<Long, BigDecimal> r = calc.aggregateIntake(items);
        // calorie: 300 + 144 = 444；protein: 20 + 13 = 33
        assertThat(r.get(1L)).isEqualByComparingTo("444");
        assertThat(r.get(2L)).isEqualByComparingTo("33");
    }

    @Test
    void 空列表返回空map() {
        assertThat(calc.aggregateIntake(List.of())).isEmpty();
    }
}
