package com.gudu.xsd.modules.aiguard;

import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.application.AiBudgetService;
import com.gudu.xsd.modules.aiguard.application.AiGuardAdminService;
import com.gudu.xsd.modules.aiguard.application.AiGuardConfigService;
import com.gudu.xsd.modules.aiguard.application.AiQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 后台概览：花费/上限/百分比/状态、各类今日次数、改限额后立即反映并留痕。 */
class AiGuardAdminServiceTest {

    AiGuardFakes.Spend spend = new AiGuardFakes.Spend();
    AiGuardFakes.Counter counter = new AiGuardFakes.Counter();
    AiGuardFakes.ConfigRepo repo = new AiGuardFakes.ConfigRepo();
    AiGuardFakes.MutableClock clock = new AiGuardFakes.MutableClock("2026-11-02T02:00:00Z");
    AiGuardAdminService admin;
    AiQuotaService quota;

    @BeforeEach
    void setUp() {
        AiGuardConfigService config = new AiGuardConfigService(repo, clock,
                AiGuardConfigService.defaultsOf(BigDecimal.valueOf(20), 80, 20, 10, 50, 50), id -> "运营小王");
        quota = new AiQuotaService(config, counter, clock);
        admin = new AiGuardAdminService(new AiBudgetService(spend, config, clock), config, quota, counter);
    }

    @Test
    void 概览_元为单位_带百分比与状态与各类次数() {
        spend.fen = new BigDecimal("1650.5");
        quota.tryConsume(AiQuotaCategory.VISION, 1L);
        quota.tryConsume(AiQuotaCategory.VISION, 2L);
        quota.tryConsume(AiQuotaCategory.MEALPLAN, 1L);
        var o = admin.overview();
        assertThat(o.spentYuan()).isEqualByComparingTo("16.51");
        assertThat(o.limitYuan()).isEqualByComparingTo("20");
        assertThat(o.percent()).isEqualTo(82);
        assertThat(o.level()).isEqualTo("WARN");
        assertThat(o.warnPercent()).isEqualTo(80);
        assertThat(o.categories()).extracting(c -> c.key()).containsExactly("vision", "mealplan", "agent", "other");
        assertThat(o.categories().get(0).todayCount()).isEqualTo(2);
        assertThat(o.categories().get(0).limitPerAccount()).isEqualTo(20);
        assertThat(o.categories().get(1).todayCount()).isEqualTo(1);
        assertThat(o.countsAvailable()).isTrue();
    }

    @Test
    void 达到上限显示PAUSED() {
        spend.fen = new BigDecimal("2000");
        assertThat(admin.overview().level()).isEqualTo("PAUSED");
    }

    @Test
    void Redis故障_次数不可用但花费照常() {
        counter.down = true;
        var o = admin.overview();
        assertThat(o.countsAvailable()).isFalse();
        assertThat(o.limitYuan()).isEqualByComparingTo("20");
    }

    @Test
    void 改限额_立即反映_日志可见() {
        var o = admin.update(9L, BigDecimal.valueOf(50), null, Map.of("vision", 5, "unknown", 1));
        assertThat(o.limitYuan()).isEqualByComparingTo("50");
        assertThat(o.categories().get(0).limitPerAccount()).isEqualTo(5);
        assertThat(o.logs()).extracting(l -> l.key()).containsExactlyInAnyOrder("budget.daily_yuan", "limit.vision");
        assertThat(o.logs().get(0).operatorName()).isEqualTo("运营小王");
    }
}
