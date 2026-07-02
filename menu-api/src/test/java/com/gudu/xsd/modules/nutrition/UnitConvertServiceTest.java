package com.gudu.xsd.modules.nutrition;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UnitConvertService 纯函数测试（算法地基，不依赖 Spring）。
 * 参照 PantryServiceTest / ShoppingAggregatorTest 范式：new UnitConvertService(Set.of(...))。
 */
class UnitConvertServiceTest {

    @Test
    void 克单位直通_无需查表() {
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        assertThat(s.toGrams(new BigDecimal("300"), 1L, null))
                .isEqualByComparingTo("300");
    }

    @Test
    void 非克单位_按换算系数算() {
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        // 2 个 × 50g/个 = 100g
        assertThat(s.toGrams(new BigDecimal("2"), 2L, new BigDecimal("50")))
                .isEqualByComparingTo("100");
    }

    @Test
    void 非克单位_未配置换算返回null() {
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        assertThat(s.toGrams(new BigDecimal("2"), 2L, null)).isNull();
    }

    @Test
    void amount为null返回null() {
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        assertThat(s.toGrams(null, 2L, new BigDecimal("50"))).isNull();
    }
}
