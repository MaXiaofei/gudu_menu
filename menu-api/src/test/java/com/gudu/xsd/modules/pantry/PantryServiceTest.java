package com.gudu.xsd.modules.pantry;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.nutrition.UnitConvertService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 食材库存测试：临期/不足纯函数 + 采购回写 planStockUp（换算/兜底/校验）。
 * 同 MealPlanServiceTest 范式：测试 new PantryService(null)，纯函数不碰 Mapper；
 * planStockUp 涉及换算时用 setUnitConvert 注入 mock UnitConvertService。
 */
class PantryServiceTest {

    private final PantryService svc = new PantryService(null);

    @Test
    void 临期判定_过期日前N天内算临期() {
        LocalDate today = LocalDate.of(2026, 6, 20);
        assertThat(svc.isExpiring(LocalDate.of(2026, 6, 22), today, 3)).isTrue();   // 2天内
        assertThat(svc.isExpiring(LocalDate.of(2026, 6, 25), today, 3)).isFalse();  // 5天，超3
        assertThat(svc.isExpiring(LocalDate.of(2026, 6, 19), today, 3)).isFalse();  // 已过期不算临期
        assertThat(svc.isExpiring(null, today, 3)).isFalse();                       // 无过期日
    }

    @Test
    void 临期判定_恰好等于临界算临期() {
        LocalDate today = LocalDate.of(2026, 6, 20);
        // 当天过期（expireDate == today）算临期；正好 today+days 也算临期（闭区间）
        assertThat(svc.isExpiring(today, today, 3)).isTrue();
        assertThat(svc.isExpiring(today.plusDays(3), today, 3)).isTrue();
    }

    @Test
    void 不足判定_余量低于阈值() {
        assertThat(svc.isLow(new BigDecimal("5"), new BigDecimal("10"))).isTrue();
        assertThat(svc.isLow(new BigDecimal("10"), new BigDecimal("10"))).isFalse(); // 等于不算不足
        assertThat(svc.isLow(new BigDecimal("15"), new BigDecimal("10"))).isFalse();
    }

    // ===================== 采购回写（planStockUp 纯函数） =====================

    @Test
    void 采购回写_amount_unitId非空_换算克数入库() {
        UnitConvertService uc = Mockito.mock(UnitConvertService.class);
        Mockito.when(uc.toGramsFor(11L, new BigDecimal("2"), 5L)).thenReturn(new BigDecimal("1000"));
        svc.setUnitConvert(uc);

        Pantry p = svc.planStockUp(11L, new BigDecimal("2"), 5L, null);

        assertThat(p.getIngredientId()).isEqualTo(11L);
        assertThat(p.getAmount()).isEqualByComparingTo("2");
        assertThat(p.getUnitId()).isEqualTo(5L);
        assertThat(p.getGrams()).isEqualByComparingTo("1000");
    }

    @Test
    void 采购回写_amount_unitId为空_用参考克数兜底() {
        Pantry p = svc.planStockUp(11L, null, null, new BigDecimal("500"));

        assertThat(p.getGrams()).isEqualByComparingTo("500");
        assertThat(p.getAmount()).isEqualByComparingTo("500");
        assertThat(p.getUnitId()).isNull();
    }

    @Test
    void 采购回写_都为空_抛异常() {
        assertThatThrownBy(() -> svc.planStockUp(11L, null, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无可入库");
    }
}
