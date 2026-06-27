package com.gudu.xsd.modules.ai;

import com.gudu.xsd.common.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AiOutputGuard 单元测试：营养值超范围 clamp、负数 clamp、正常通过、离谱值拒绝。
 */
class AiOutputGuardTest {

    private final AiOutputGuard guard = new AiOutputGuard();

    // metricId: 1cal/2protein/3fat/4carb/5sugar/6gi

    @Test
    void per100g_正常范围_通过() {
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(1L, new BigDecimal("19"));    // calorie
        nut.put(2L, new BigDecimal("0.9"));   // protein
        nut.put(3L, new BigDecimal("0.2"));   // fat
        nut.put(4L, new BigDecimal("4.0"));   // carb
        nut.put(5L, new BigDecimal("2.6"));   // sugar
        nut.put(6L, new BigDecimal("30"));    // gi
        var out = guard.validateNutrition(nut, true);
        assertThat(out.get(1L)).isEqualByComparingTo("19");
        assertThat(out.get(6L)).isEqualByComparingTo("30");
    }

    @Test
    void per100g_负数_clamp到0() {
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(1L, new BigDecimal("-10"));
        nut.put(2L, new BigDecimal("-0.5"));
        var out = guard.validateNutrition(nut, true);
        assertThat(out.get(1L)).isEqualByComparingTo("0");
        assertThat(out.get(2L)).isEqualByComparingTo("0");
    }

    @Test
    void per100g_轻微超范围_clamp() {
        // calorie per100g 上限 3000，超一点 clamp 到 3000
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(1L, new BigDecimal("3500"));
        var out = guard.validateNutrition(nut, true);
        assertThat(out.get(1L)).isEqualByComparingTo("3000");
    }

    @Test
    void per100g_离谱超范围_拒绝() {
        // calorie per100g 给 100000，明显离谱 → BizException
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(1L, new BigDecimal("100000"));
        assertThatThrownBy(() -> guard.validateNutrition(nut, true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("异常");
    }

    @Test
    void per100g_gi_超100_clamp() {
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(6L, new BigDecimal("120"));
        var out = guard.validateNutrition(nut, true);
        assertThat(out.get(6L)).isEqualByComparingTo("100");
    }

    @Test
    void 整餐_正常范围_通过() {
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(1L, new BigDecimal("350"));    // calorie 整餐
        nut.put(2L, new BigDecimal("18"));
        nut.put(5L, new BigDecimal("6"));
        var out = guard.validateNutrition(nut, false);
        assertThat(out.get(1L)).isEqualByComparingTo("350");
    }

    @Test
    void 整餐_超范围_clamp() {
        // 整餐 calorie 上限 20000
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(1L, new BigDecimal("25000"));
        var out = guard.validateNutrition(nut, false);
        assertThat(out.get(1L)).isEqualByComparingTo("20000");
    }

    @Test
    void 整餐_轻微蛋白超_range_clamp() {
        // 整餐 protein 上限 2000
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(2L, new BigDecimal("2100"));
        var out = guard.validateNutrition(nut, false);
        assertThat(out.get(2L)).isEqualByComparingTo("2000");
    }

    @Test
    void 整餐_离谱_拒绝() {
        Map<Long, BigDecimal> nut = new LinkedHashMap<>();
        nut.put(1L, new BigDecimal("9999999"));
        assertThatThrownBy(() -> guard.validateNutrition(nut, false))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 空map_通过() {
        assertThatCode(() -> guard.validateNutrition(Map.of(), true)).doesNotThrowAnyException();
    }
}
