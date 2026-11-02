package com.gudu.xsd.modules.aiguard.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.domain.model.AiGuardConfig;
import com.gudu.xsd.modules.aiguard.domain.repository.AiGuardConfigRepository;
import com.gudu.xsd.modules.aiguard.domain.repository.AiGuardConfigRepository.Change;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI 额度配置：库里只存运营改过的键，其余取应用配置默认值；本地缓存 {@link #CACHE_TTL}（改动本实例立即生效，
 * 其他实例最迟 60 秒）。库读失败沿用上一份缓存（没有则默认值），不因配置读不到而放开或卡死 AI。
 */
@Slf4j
@Service
public class AiGuardConfigService {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    /** 预算上限的合理范围（元）。 */
    static final BigDecimal MAX_BUDGET_YUAN = BigDecimal.valueOf(1_000_000);
    static final int MAX_LIMIT = 100_000;

    /** 一次修改请求；字段为 null = 不改。 */
    public record Update(BigDecimal dailyBudgetYuan, Integer warnPercent, Map<AiQuotaCategory, Integer> limits) {
    }

    private final AiGuardConfigRepository repository;
    private final Clock clock;
    private final AiGuardConfig defaults;
    private final Function<Long, String> operatorNames;

    private volatile AiGuardConfig cached;
    private volatile Instant loadedAt = Instant.MIN;

    @Autowired
    public AiGuardConfigService(AiGuardConfigRepository repository, Clock clock,
                                ObjectProvider<AccountQueryApi> accounts,
                                @Value("${gudu.ai.budget.daily-yuan:20}") BigDecimal dailyBudgetYuan,
                                @Value("${gudu.ai.budget.warn-percent:80}") int warnPercent,
                                @Value("${gudu.ai.vision.daily-limit:20}") int visionLimit,
                                @Value("${gudu.ai.mealplan.daily-limit:10}") int mealplanLimit,
                                @Value("${gudu.agent.daily-limit:${gudu.ai.daily-limit:50}}") int agentLimit,
                                @Value("${gudu.ai.daily-limit:50}") int otherLimit) {
        this(repository, clock, defaultsOf(dailyBudgetYuan, warnPercent, visionLimit, mealplanLimit, agentLimit,
                otherLimit), id -> operatorName(accounts, id));
    }

    /** 单测 / 手工装配。 */
    public AiGuardConfigService(AiGuardConfigRepository repository, Clock clock, AiGuardConfig defaults,
                                Function<Long, String> operatorNames) {
        this.repository = repository;
        this.clock = clock;
        this.defaults = defaults;
        this.operatorNames = operatorNames;
    }

    public static AiGuardConfig defaultsOf(BigDecimal budgetYuan, int warnPercent, int vision, int mealplan, int agent,
                                    int other) {
        Map<AiQuotaCategory, Integer> m = new EnumMap<>(AiQuotaCategory.class);
        m.put(AiQuotaCategory.VISION, vision);
        m.put(AiQuotaCategory.MEALPLAN, mealplan);
        m.put(AiQuotaCategory.AGENT, agent);
        m.put(AiQuotaCategory.OTHER, other);
        return new AiGuardConfig(budgetYuan, warnPercent, m);
    }

    /** 当前生效配置（≤60 秒缓存）。 */
    public AiGuardConfig current() {
        AiGuardConfig c = cached;
        Instant now = clock.instant();
        if (c != null && Duration.between(loadedAt, now).compareTo(CACHE_TTL) < 0) return c;
        try {
            c = merge(repository.loadAll());
        } catch (RuntimeException e) {
            log.warn("读取 AI 额度配置失败，沿用{}: {}", cached == null ? "默认值" : "上一份缓存", e.getMessage());
            c = cached != null ? cached : defaults;
        }
        cached = c;
        loadedAt = now;
        return c;
    }

    /** 默认值（后台「恢复默认」参考）。 */
    public AiGuardConfig defaults() {
        return defaults;
    }

    /** 修改并留痕（操作人、时间、新旧值）；只记录真正变化的键；立即使本实例缓存失效。 */
    public AiGuardConfig update(Long operatorId, Update req) {
        if (req == null) throw new BizException("没有要修改的内容");
        AiGuardConfig now = current();
        List<Change> changes = new ArrayList<>();
        if (req.dailyBudgetYuan() != null) {
            BigDecimal v = req.dailyBudgetYuan().stripTrailingZeros();
            if (v.signum() < 0 || v.compareTo(MAX_BUDGET_YUAN) > 0) throw new BizException("每日花费上限需在 0～1000000 元之间（0 = 不限）");
            addChange(changes, AiGuardConfig.KEY_BUDGET, now.dailyBudgetYuan(), v);
        }
        if (req.warnPercent() != null) {
            if (req.warnPercent() < 1 || req.warnPercent() > 100) throw new BizException("提醒比例需在 1～100 之间");
            addChange(changes, AiGuardConfig.KEY_WARN, now.warnPercent(), req.warnPercent());
        }
        if (req.limits() != null) {
            for (Map.Entry<AiQuotaCategory, Integer> e : req.limits().entrySet()) {
                Integer v = e.getValue();
                if (v == null) continue;
                if (v < 0 || v > MAX_LIMIT) throw new BizException(e.getKey().label() + "次数上限需在 0～" + MAX_LIMIT + " 之间（0 = 不限）");
                addChange(changes, AiGuardConfig.limitKey(e.getKey()), now.limit(e.getKey()), v);
            }
        }
        if (!changes.isEmpty()) {
            repository.saveAll(changes, operatorId, operatorNames.apply(operatorId), LocalDateTime.now(clock));
            log.warn("AI 额度配置被修改，操作人 {}：{}", operatorId, changes);
        }
        invalidate();
        return current();
    }

    public void invalidate() {
        loadedAt = Instant.MIN;
    }

    /** 最近的修改日志。 */
    public List<AiGuardConfigRepository.ChangeLog> recentLogs(int limit) {
        try {
            return repository.recentLogs(limit);
        } catch (RuntimeException e) {
            log.warn("读取 AI 额度修改日志失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static void addChange(List<Change> changes, String key, Object oldEffective, Object next) {
        String nv = plain(next);
        if (!nv.equals(plain(oldEffective))) changes.add(new Change(key, plain(oldEffective), nv));
    }

    private static String plain(Object v) {
        return v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : String.valueOf(v);
    }

    private AiGuardConfig merge(Map<String, String> stored) {
        BigDecimal budget = parseDecimal(stored.get(AiGuardConfig.KEY_BUDGET), defaults.dailyBudgetYuan());
        int warn = parseInt(stored.get(AiGuardConfig.KEY_WARN), defaults.warnPercent());
        Map<AiQuotaCategory, Integer> limits = new EnumMap<>(AiQuotaCategory.class);
        for (AiQuotaCategory c : AiQuotaCategory.values()) {
            limits.put(c, parseInt(stored.get(AiGuardConfig.limitKey(c)), defaults.limit(c)));
        }
        return new AiGuardConfig(budget, warn, limits);
    }

    private static BigDecimal parseDecimal(String s, BigDecimal fallback) {
        try {
            return s == null ? fallback : new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String operatorName(ObjectProvider<AccountQueryApi> accounts, Long id) {
        AccountQueryApi api = accounts.getIfAvailable();
        if (api == null || id == null) return null;
        try {
            var v = api.views(List.of(id)).get(id);
            return v == null ? null : v.nickname();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
