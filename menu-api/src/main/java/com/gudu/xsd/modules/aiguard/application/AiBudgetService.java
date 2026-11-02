package com.gudu.xsd.modules.aiguard.application;

import com.gudu.xsd.modules.ai.gateway.domain.port.AiBudgetGuard;
import com.gudu.xsd.modules.aiguard.domain.model.AiGuardConfig;
import com.gudu.xsd.modules.aiguard.domain.model.BudgetStatus;
import com.gudu.xsd.modules.aiguard.domain.port.AiSpendReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 全站每日花费闸门。今日已花费 = ai_call_log 当日（北京时间）cost_fen 之和，直接查库并缓存 {@link #CACHE_TTL}。
 *
 * <p>取舍：不另维护 Redis 累加器——库是唯一真相、多实例天然一致、重启和跨日无需校准；代价是最多
 * {@link #CACHE_TTL} 内的花费不可见，每次调用仅几厘到几分，超支量级可忽略（上限本身是软保护）。
 * 库读失败时沿用上一次读数（没有则按未超），不因统计故障把全站 AI 停掉。
 *
 * <p>达到提醒比例（默认 80%）时打一次 warn 日志（每个北京日期每实例一次）；次日日期变化，花费重新从 0 算起，自动恢复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBudgetService implements AiBudgetGuard {

    static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final AiSpendReader spendReader;
    private final AiGuardConfigService config;
    private final Clock clock;

    private volatile Reading reading;
    private volatile LocalDate warnedDay;

    private record Reading(LocalDate day, BigDecimal spentFen, Instant at) {
    }

    @Override
    public boolean exhausted() {
        return status().exhausted();
    }

    /** 当前花费状态；顺带触发 80% 提醒日志。 */
    public BudgetStatus status() {
        AiGuardConfig cfg = config.current();
        LocalDate day = clock.instant().atZone(AiQuotaService.ZONE).toLocalDate();
        BudgetStatus s = BudgetStatus.of(spentFen(day), cfg.budgetFen(), cfg.warnPercent());
        if (s.level() != BudgetStatus.Level.NORMAL && !day.equals(warnedDay)) {
            warnedDay = day;
            log.warn("AI 今日花费已达 {}%（{} 分 / 上限 {} 分，{}），{}", s.percent(), s.spentFen().setScale(2, java.math.RoundingMode.HALF_UP),
                    s.limitFen().stripTrailingZeros().toPlainString(), day,
                    s.exhausted() ? "已暂停调用模型，次日自动恢复" : "请关注");
        }
        return s;
    }

    private BigDecimal spentFen(LocalDate day) {
        Reading r = reading;
        Instant now = clock.instant();
        if (r != null && r.day().equals(day) && Duration.between(r.at(), now).compareTo(CACHE_TTL) < 0) {
            return r.spentFen();
        }
        try {
            BigDecimal v = spendReader.spentFenSince(day.atStartOfDay());
            reading = new Reading(day, v == null ? BigDecimal.ZERO : v, now);
            return reading.spentFen();
        } catch (RuntimeException e) {
            log.warn("读取今日 AI 花费失败，沿用上次读数: {}", e.getMessage());
            // 跨日后的旧读数不能沿用到新的一天
            return r != null && r.day().equals(day) ? r.spentFen() : BigDecimal.ZERO;
        }
    }
}
