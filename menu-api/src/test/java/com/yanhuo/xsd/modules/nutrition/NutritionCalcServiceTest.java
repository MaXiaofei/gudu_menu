package com.yanhuo.xsd.modules.nutrition;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 营养聚合纯函数测试：单道菜营养 = Σ(食材营养 per100g × 用量g / 100)，可整体按份数缩放。
 */
class NutritionCalcServiceTest {

    private final NutritionCalcService calc = new NutritionCalcService();

    @Test
    void 单道菜营养_按用量缩放后求和() {
        // 番茄 200g: calorie 19/100g, carb 4/100g
        // 鸡蛋 100g: calorie 144/100g, protein 13/100g
        var dish = List.of(
                new NutritionCalcService.Item(1L, new BigDecimal("19"),  new BigDecimal("200")),
                new NutritionCalcService.Item(4L, new BigDecimal("4"),   new BigDecimal("200")),
                new NutritionCalcService.Item(1L, new BigDecimal("144"), new BigDecimal("100")),
                new NutritionCalcService.Item(2L, new BigDecimal("13"),  new BigDecimal("100"))
        );
        Map<Long, BigDecimal> r = calc.aggregateDish(dish);
        // calorie: 19*200/100 + 144*100/100 = 38 + 144 = 182
        // carb:    4*200/100 = 8
        // protein: 13*100/100 = 13
        assertThat(r.get(1L)).isEqualByComparingTo("182");
        assertThat(r.get(4L)).isEqualByComparingTo("8");
        assertThat(r.get(2L)).isEqualByComparingTo("13");
    }

    @Test
    void 份数缩放_整体乘以factor() {
        var dish = List.of(new NutritionCalcService.Item(1L, new BigDecimal("100"), new BigDecimal("100")));
        // 100/100*100 = 100，再 ×3 份 = 300
        assertThat(calc.aggregateDish(dish, new BigDecimal("3")).get(1L)).isEqualByComparingTo("300");
    }
}
