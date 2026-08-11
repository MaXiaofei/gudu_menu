package com.gudu.xsd.modules.menu;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 菜单汇总纯函数测试（V55 价格链路删除后）：营养 = Σ(各菜份数营养按指标累加)。
 */
class MenuCalcServiceTest {

    private final MenuCalcService calc = new MenuCalcService();

    @Test
    void 菜单营养_各菜份数营养按指标累加() {
        var lines = List.of(
                new MenuCalcService.MenuLine(Map.of(1L, new BigDecimal("182")), new BigDecimal("2")), // 364
                new MenuCalcService.MenuLine(Map.of(1L, new BigDecimal("100")), new BigDecimal("1"))  // 100
        );
        // 182*2 + 100*1 = 464
        assertThat(calc.totalNutrition(lines).get(1L)).isEqualByComparingTo("464");
    }

    @Test
    void 多指标_各指标独立累加() {
        var lines = List.of(
                new MenuCalcService.MenuLine(Map.of(1L, new BigDecimal("100"), 2L, new BigDecimal("10")), BigDecimal.ONE),
                new MenuCalcService.MenuLine(Map.of(2L, new BigDecimal("5")), BigDecimal.ONE));
        var sum = calc.totalNutrition(lines);
        assertThat(sum.get(1L)).isEqualByComparingTo("100");
        assertThat(sum.get(2L)).isEqualByComparingTo("15");
    }
}
