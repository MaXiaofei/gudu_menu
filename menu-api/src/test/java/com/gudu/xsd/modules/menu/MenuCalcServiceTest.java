package com.gudu.xsd.modules.menu;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 菜单汇总纯函数测试：总价 = Σ(菜品价 × 份数)；营养 = Σ(各菜份数营养按指标累加)。
 */
class MenuCalcServiceTest {

    private final MenuCalcService calc = new MenuCalcService();

    @Test
    void 菜单总价_按份数累加() {
        var lines = List.of(
                new MenuCalcService.MenuLine(new BigDecimal("10"), Map.of(), new BigDecimal("2")),
                new MenuCalcService.MenuLine(new BigDecimal("15"), Map.of(), new BigDecimal("1"))
        );
        // 10*2 + 15*1 = 35
        assertThat(calc.totalPrice(lines)).isEqualByComparingTo("35");
    }

    @Test
    void 菜单营养_各菜份数营养按指标累加() {
        var lines = List.of(
                new MenuCalcService.MenuLine(new BigDecimal("0"), Map.of(1L, new BigDecimal("182")), new BigDecimal("2")), // 364
                new MenuCalcService.MenuLine(new BigDecimal("0"), Map.of(1L, new BigDecimal("100")), new BigDecimal("1"))  // 100
        );
        // 182*2 + 100*1 = 464
        assertThat(calc.totalNutrition(lines).get(1L)).isEqualByComparingTo("464");
    }
}
