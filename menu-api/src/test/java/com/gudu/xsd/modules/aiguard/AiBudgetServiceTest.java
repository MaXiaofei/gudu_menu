package com.gudu.xsd.modules.aiguard;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gudu.xsd.modules.aiguard.application.AiBudgetService;
import com.gudu.xsd.modules.aiguard.application.AiGuardConfigService;
import com.gudu.xsd.modules.aiguard.domain.model.BudgetStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 全站每日预算：状态分级、80% 提醒每天只打一次、跨日恢复、读库缓存与故障。 */
class AiBudgetServiceTest {

    AiGuardFakes.Spend spend;
    AiGuardFakes.MutableClock clock;
    AiBudgetService budget;
    ListAppender<ILoggingEvent> logs;
    Logger logger;

    @BeforeEach
    void setUp() {
        spend = new AiGuardFakes.Spend();
        // 2026-11-02 10:00 北京时间；默认预算 20 元 = 2000 分，提醒 80% = 1600 分
        clock = new AiGuardFakes.MutableClock("2026-11-02T02:00:00Z");
        AiGuardConfigService config = new AiGuardConfigService(new AiGuardFakes.ConfigRepo(), clock,
                AiGuardConfigService.defaultsOf(BigDecimal.valueOf(20), 80, 20, 10, 50, 50), id -> null);
        budget = new AiBudgetService(spend, config, clock);
        logger = (Logger) LoggerFactory.getLogger(AiBudgetService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private long warns() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("今日花费已达")).count();
    }

    @Test
    void 分级_正常_提醒_暂停() {
        spend.fen = new BigDecimal("1599");
        assertThat(budget.status().level()).isEqualTo(BudgetStatus.Level.NORMAL);
        clock.plusSeconds(11);
        spend.fen = new BigDecimal("1600");
        BudgetStatus s = budget.status();
        assertThat(s.level()).isEqualTo(BudgetStatus.Level.WARN);
        assertThat(s.percent()).isEqualTo(80);
        assertThat(budget.exhausted()).isFalse();
        clock.plusSeconds(11);
        spend.fen = new BigDecimal("2000");
        assertThat(budget.exhausted()).isTrue();
        assertThat(budget.status().percent()).isEqualTo(100);
    }

    @Test
    void 提醒日志每天只打一次_次日再打() {
        spend.fen = new BigDecimal("1700");
        budget.status();
        clock.plusSeconds(11);
        budget.status();
        spend.fen = new BigDecimal("2100"); // 升到暂停也不重复打
        clock.plusSeconds(11);
        budget.status();
        assertThat(warns()).isEqualTo(1);
        clock.plusSeconds(24 * 3600); // 次日
        budget.status();
        assertThat(warns()).isEqualTo(2);
    }

    @Test
    void 跨日自动恢复_按北京时间当天零点起算() {
        spend.fen = new BigDecimal("2500");
        assertThat(budget.exhausted()).isTrue();
        assertThat(spend.lastFrom).isEqualTo(LocalDateTime.parse("2026-11-02T00:00:00"));
        clock.plusSeconds(24 * 3600);
        spend.fen = BigDecimal.ZERO; // 新的一天，当日 cost_fen 之和从 0 起
        assertThat(budget.exhausted()).isFalse();
        assertThat(spend.lastFrom).isEqualTo(LocalDateTime.parse("2026-11-03T00:00:00"));
    }

    @Test
    void 读库有10秒缓存() {
        spend.fen = new BigDecimal("100");
        budget.status();
        budget.status();
        assertThat(spend.reads).isEqualTo(1);
        clock.plusSeconds(11);
        budget.status();
        assertThat(spend.reads).isEqualTo(2);
    }

    @Test
    void 读库失败_沿用上次读数_跨日则按零() {
        spend.fen = new BigDecimal("2000");
        assertThat(budget.exhausted()).isTrue();
        spend.failing = true;
        clock.plusSeconds(11);
        assertThat(budget.exhausted()).as("同一天沿用上次读数").isTrue();
        clock.plusSeconds(24 * 3600);
        assertThat(budget.exhausted()).as("新的一天旧读数作废").isFalse();
    }

    @Test
    void 预算0_不限() {
        AiGuardFakes.ConfigRepo repo = new AiGuardFakes.ConfigRepo();
        repo.stored.put("budget.daily_yuan", "0");
        AiGuardConfigService config = new AiGuardConfigService(repo, clock,
                AiGuardConfigService.defaultsOf(BigDecimal.valueOf(20), 80, 20, 10, 50, 50), id -> null);
        AiBudgetService b = new AiBudgetService(spend, config, clock);
        spend.fen = new BigDecimal("999999");
        assertThat(b.exhausted()).isFalse();
        assertThat(b.status().percent()).isZero();
    }
}
