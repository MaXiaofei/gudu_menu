package com.gudu.xsd.modules.aiguard.application;

import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.application.AiGuardConfigService.Update;
import com.gudu.xsd.modules.aiguard.domain.model.AiGuardConfig;
import com.gudu.xsd.modules.aiguard.domain.model.BudgetStatus;
import com.gudu.xsd.modules.aiguard.domain.port.AiUsageCounter;
import com.gudu.xsd.modules.aiguard.domain.repository.AiGuardConfigRepository.ChangeLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理后台「AI 用量与额度」：今日花费 / 各类调用次数 / 当前配置 / 修改日志。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGuardAdminService {

    private static final int RECENT_LOGS = 20;

    private final AiBudgetService budget;
    private final AiGuardConfigService config;
    private final AiQuotaService quota;
    private final AiUsageCounter counter;

    /** 一个类别的今日用量与上限。 */
    public record CategoryUsage(String key, String label, long todayCount, int limitPerAccount) {
    }

    /** 概览。level：NORMAL / WARN / PAUSED；limitYuan = 0 表示不限。 */
    public record Overview(BigDecimal spentYuan, BigDecimal limitYuan, int percent, String level, int warnPercent,
                           List<CategoryUsage> categories, boolean countsAvailable, List<ChangeLog> logs) {
    }

    public Overview overview() {
        BudgetStatus s = budget.status();
        AiGuardConfig cfg = config.current();
        Map<AiQuotaCategory, Long> totals = Map.of();
        boolean ok = true;
        try {
            totals = counter.totals(quota.today());
        } catch (RuntimeException e) {
            ok = false;
            log.warn("读取今日 AI 调用次数失败: {}", e.getMessage());
        }
        Map<AiQuotaCategory, Long> t = totals;
        List<CategoryUsage> cats = java.util.Arrays.stream(AiQuotaCategory.values())
                .map(c -> new CategoryUsage(c.name().toLowerCase(), c.label(), t.getOrDefault(c, 0L), cfg.limit(c)))
                .toList();
        return new Overview(yuan(s.spentFen()), yuan(s.limitFen()), s.percent(), s.level().name(), cfg.warnPercent(),
                cats, ok, config.recentLogs(RECENT_LOGS));
    }

    /** 改限额：按字段名（vision / mealplan / agent / other）；未知字段名忽略。 */
    public Overview update(Long operatorId, BigDecimal dailyBudgetYuan, Integer warnPercent,
                           Map<String, Integer> limitsByKey) {
        Map<AiQuotaCategory, Integer> limits = new LinkedHashMap<>();
        if (limitsByKey != null) {
            for (AiQuotaCategory c : AiQuotaCategory.values()) {
                Integer v = limitsByKey.get(c.name().toLowerCase());
                if (v != null) limits.put(c, v);
            }
        }
        config.update(operatorId, new Update(dailyBudgetYuan, warnPercent, limits));
        return overview();
    }

    private static BigDecimal yuan(BigDecimal fen) {
        return fen.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
