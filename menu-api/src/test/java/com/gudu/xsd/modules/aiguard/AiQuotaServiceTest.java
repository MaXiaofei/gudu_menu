package com.gudu.xsd.modules.aiguard;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.application.AiGuardConfigService;
import com.gudu.xsd.modules.aiguard.application.AiQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 次数配额：每账号每类每天独立计数、超限 429、跨日恢复、Redis 故障拒绝。 */
class AiQuotaServiceTest {

    AiGuardFakes.Counter counter;
    AiGuardFakes.ConfigRepo repo;
    AiGuardFakes.MutableClock clock;
    AiQuotaService quota;

    @BeforeEach
    void setUp() {
        counter = new AiGuardFakes.Counter();
        repo = new AiGuardFakes.ConfigRepo();
        // 2026-11-02 23:00 北京时间
        clock = new AiGuardFakes.MutableClock("2026-11-02T15:00:00Z");
        AiGuardConfigService config = new AiGuardConfigService(repo, clock,
                AiGuardConfigService.defaultsOf(BigDecimal.valueOf(20), 80, 20, 10, 50, 50), id -> null);
        quota = new AiQuotaService(config, counter, clock);
    }

    private static int code(Throwable e) {
        return ((BizException) e).getCode();
    }

    @Test
    void 拍照每天20次_第21次429且提示可读() {
        for (int i = 0; i < 20; i++) quota.require(AiQuotaCategory.VISION, 7L);
        assertThatThrownBy(() -> quota.require(AiQuotaCategory.VISION, 7L))
                .isInstanceOf(BizException.class).hasMessage("今日拍照识别次数已用完，明天再试")
                .satisfies(e -> assertThat(code(e)).isEqualTo(429));
    }

    @Test
    void 餐程每天10次_第11次429() {
        for (int i = 0; i < 10; i++) quota.require(AiQuotaCategory.MEALPLAN, 7L);
        assertThatThrownBy(() -> quota.require(AiQuotaCategory.MEALPLAN, 7L))
                .hasMessage("今日一周餐程次数已用完，明天再试");
    }

    @Test
    void 各类各账号互不影响() {
        for (int i = 0; i < 10; i++) quota.require(AiQuotaCategory.MEALPLAN, 7L);
        quota.require(AiQuotaCategory.VISION, 7L);
        quota.require(AiQuotaCategory.MEALPLAN, 8L);
        assertThat(quota.tryConsume(AiQuotaCategory.MEALPLAN, 7L)).isFalse();
    }

    @Test
    void 其他AI沿用原提示文案() {
        for (int i = 0; i < 50; i++) quota.require(AiQuotaCategory.OTHER, 7L);
        assertThatThrownBy(() -> quota.require(AiQuotaCategory.OTHER, 7L)).hasMessage("今日 AI 调用已达上限（50 次），明天再试");
    }

    @Test
    void 咕嘟tryConsume_超限返回false不抛() {
        for (int i = 0; i < 50; i++) assertThat(quota.tryConsume(AiQuotaCategory.AGENT, 7L)).isTrue();
        assertThat(quota.tryConsume(AiQuotaCategory.AGENT, 7L)).isFalse();
    }

    @Test
    void 跨日按北京时间恢复() {
        for (int i = 0; i < 10; i++) quota.require(AiQuotaCategory.MEALPLAN, 7L);
        assertThat(quota.tryConsume(AiQuotaCategory.MEALPLAN, 7L)).isFalse();
        clock.plusSeconds(3601); // 过了北京时间零点（UTC 16:00 之后）
        assertThat(quota.tryConsume(AiQuotaCategory.MEALPLAN, 7L)).isTrue();
    }

    @Test
    void Redis故障_拒绝_failClosed() {
        counter.down = true;
        assertThatThrownBy(() -> quota.require(AiQuotaCategory.VISION, 7L)).isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo(503));
        assertThat(quota.tryConsume(AiQuotaCategory.AGENT, 7L)).isFalse();
    }

    @Test
    void 没有账号拒绝() {
        assertThat(quota.tryConsume(AiQuotaCategory.VISION, null)).isFalse();
    }

    @Test
    void 上限改为0_不限() {
        repo.stored.put("limit.vision", "0");
        for (int i = 0; i < 100; i++) quota.require(AiQuotaCategory.VISION, 7L);
    }

    @Test
    void 放行次数计入全站统计_被拒的不计() {
        for (int i = 0; i < 12; i++) quota.tryConsume(AiQuotaCategory.MEALPLAN, 7L);
        assertThat(counter.totals(quota.today()).get(AiQuotaCategory.MEALPLAN)).isEqualTo(10L);
    }
}
