package com.gudu.xsd.modules.aiguard;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.application.AiGuardConfigService;
import com.gudu.xsd.modules.aiguard.application.AiGuardConfigService.Update;
import com.gudu.xsd.modules.aiguard.domain.model.AiGuardConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 配置：默认值、库里覆盖、60 秒缓存、修改留痕与校验、库故障沿用缓存。 */
class AiGuardConfigServiceTest {

    AiGuardFakes.ConfigRepo repo;
    AiGuardFakes.MutableClock clock;
    AiGuardConfigService svc;

    @BeforeEach
    void setUp() {
        repo = new AiGuardFakes.ConfigRepo();
        clock = new AiGuardFakes.MutableClock("2026-11-02T02:00:00Z");
        svc = new AiGuardConfigService(repo, clock, AiGuardConfigService.defaultsOf(BigDecimal.valueOf(20), 80, 20, 10, 50, 50),
                id -> "运营小王");
    }

    @Test
    void 库里没有时取默认值() {
        AiGuardConfig c = svc.current();
        assertThat(c.dailyBudgetYuan()).isEqualByComparingTo("20");
        assertThat(c.warnPercent()).isEqualTo(80);
        assertThat(c.limit(AiQuotaCategory.VISION)).isEqualTo(20);
        assertThat(c.limit(AiQuotaCategory.MEALPLAN)).isEqualTo(10);
        assertThat(c.limit(AiQuotaCategory.AGENT)).isEqualTo(50);
        assertThat(c.limit(AiQuotaCategory.OTHER)).isEqualTo(50);
        assertThat(c.budgetFen()).isEqualByComparingTo("2000");
    }

    @Test
    void 库里的值覆盖默认值_脏值回退默认() {
        repo.stored.put("limit.vision", "5");
        repo.stored.put("budget.daily_yuan", "8.5");
        repo.stored.put("limit.mealplan", "abc");
        AiGuardConfig c = svc.current();
        assertThat(c.limit(AiQuotaCategory.VISION)).isEqualTo(5);
        assertThat(c.dailyBudgetYuan()).isEqualByComparingTo("8.5");
        assertThat(c.limit(AiQuotaCategory.MEALPLAN)).isEqualTo(10);
    }

    @Test
    void 缓存60秒内不重读库_过期后重读() {
        svc.current();
        svc.current();
        assertThat(repo.loads).isEqualTo(1);
        repo.stored.put("limit.vision", "7");
        clock.plusSeconds(59);
        assertThat(svc.current().limit(AiQuotaCategory.VISION)).as("缓存未过期仍是旧值").isEqualTo(20);
        clock.plusSeconds(2);
        assertThat(svc.current().limit(AiQuotaCategory.VISION)).isEqualTo(7);
        assertThat(repo.loads).isEqualTo(2);
    }

    @Test
    void 修改后本实例立即生效_只记真正变化的键_留痕含操作人与新旧值() {
        svc.current();
        AiGuardConfig after = svc.update(9L, new Update(BigDecimal.valueOf(30), 80,
                Map.of(AiQuotaCategory.VISION, 20, AiQuotaCategory.MEALPLAN, 3)));
        assertThat(after.dailyBudgetYuan()).isEqualByComparingTo("30");
        assertThat(after.limit(AiQuotaCategory.MEALPLAN)).isEqualTo(3);
        // 提醒比例与拍照上限没变，不记
        assertThat(repo.logs).extracting(l -> l.key()).containsExactlyInAnyOrder("budget.daily_yuan", "limit.mealplan");
        var l = repo.logs.stream().filter(x -> x.key().equals("limit.mealplan")).findFirst().orElseThrow();
        assertThat(l.oldValue()).isEqualTo("10");
        assertThat(l.newValue()).isEqualTo("3");
        assertThat(l.operatorId()).isEqualTo(9L);
        assertThat(l.operatorName()).isEqualTo("运营小王");
        assertThat(l.at()).isNotNull();
    }

    @Test
    void 非法值被拒绝且不落库() {
        assertThatThrownBy(() -> svc.update(1L, new Update(BigDecimal.valueOf(-1), null, null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.update(1L, new Update(null, 0, null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.update(1L, new Update(null, 101, null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.update(1L, new Update(null, null, Map.of(AiQuotaCategory.VISION, -2))))
                .isInstanceOf(BizException.class);
        assertThat(repo.stored).isEmpty();
        assertThat(repo.logs).isEmpty();
    }

    @Test
    void 库读失败_沿用上一份缓存_没有缓存用默认值() {
        repo.failing = true;
        assertThat(svc.current().limit(AiQuotaCategory.VISION)).isEqualTo(20);
        repo.failing = false;
        repo.stored.put("limit.vision", "9");
        clock.plusSeconds(61);
        assertThat(svc.current().limit(AiQuotaCategory.VISION)).isEqualTo(9);
        repo.failing = true;
        clock.plusSeconds(61);
        assertThat(svc.current().limit(AiQuotaCategory.VISION)).as("沿用上一份").isEqualTo(9);
    }
}
