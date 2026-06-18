package com.yanhuo.xsd.modules.pantry;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 食材库存纯函数测试：临期判定 isExpiring / 不足判定 isLow。
 * 同 MealPlanServiceTest 范式：测试 new PantryService(null)，
 * Service 持有公开 Mapper 字段（@Autowired 主构造对齐测试的单参构造签名）。
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
}
