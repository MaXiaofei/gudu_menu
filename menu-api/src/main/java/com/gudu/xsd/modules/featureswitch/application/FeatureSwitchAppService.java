package com.gudu.xsd.modules.featureswitch.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureSwitch;
import com.gudu.xsd.modules.featureswitch.domain.repository.FeatureSwitchRepository;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 switch_key 通用的紧急关停开关服务（咕嘟助手、问咕嘟）。
 *
 * <p>只用于出现内容风险时一键关闭，默认开、提审版本也开。
 * 读路径走进程内短缓存 {@link #CACHE_TTL}：本实例切换立即生效，其他实例最迟 TTL 后生效。
 * 读库失败时沿用上次已知值（从未读到则视为开）。
 */
@Slf4j
@Service
public class FeatureSwitchAppService {

    public static final Duration CACHE_TTL = Duration.ofSeconds(15);
    public static final int RECENT_CHANGES = 10;

    private final FeatureSwitchRepository repository;
    private final ObjectProvider<AccountQueryApi> accounts;
    private final Clock clock;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public FeatureSwitchAppService(FeatureSwitchRepository repository, ObjectProvider<AccountQueryApi> accounts,
                                   Clock clock) {
        this.repository = repository;
        this.accounts = accounts;
        this.clock = clock;
    }

    private record Cached(boolean enabled, Instant expiresAt) {
    }

    /** 管理后台：一个开关的当前状态与最近修改记录。 */
    public record SwitchView(String key, String label, boolean enabled, Long updatedBy, String updatedByName,
                             LocalDateTime updatedAt, String reason, List<FeatureSwitch> recentChanges) {
    }

    /** 用户侧只读状态（客户端据此隐藏入口）。 */
    public record PublicStatus(boolean agentChat, boolean cookAsk) {
    }

    /** 是否开放（带短缓存，拦截判定用）。 */
    public boolean isEnabled(FeatureKey key) {
        Instant now = clock.instant();
        Cached c = cache.get(key.key());
        if (c != null && now.isBefore(c.expiresAt())) return c.enabled();
        boolean enabled;
        try {
            enabled = repository.load(key.key()).map(FeatureSwitch::enabled).orElse(true);
        } catch (RuntimeException e) {
            enabled = c == null || c.enabled();
            log.warn("读取开关 {} 失败，沿用 {}：{}", key.key(), enabled ? "开" : "关", e.getMessage());
        }
        cache.put(key.key(), new Cached(enabled, now.plus(CACHE_TTL)));
        return enabled;
    }

    /** 用户侧只读状态。 */
    public PublicStatus publicStatus() {
        return new PublicStatus(isEnabled(FeatureKey.AGENT_CHAT), isEnabled(FeatureKey.AGENT_COOK_ASK));
    }

    /** 管理后台：全部开关（不走缓存）。 */
    public List<SwitchView> viewAll() {
        return Arrays.stream(FeatureKey.values()).map(this::view).toList();
    }

    public SwitchView view(FeatureKey key) {
        FeatureSwitch s = repository.load(key.key()).orElse(FeatureSwitch.defaultOn(key.key()));
        return new SwitchView(key.key(), key.label(), s.enabled(), s.updatedBy(), s.updatedByName(), s.updatedAt(),
                s.reason(), repository.recentChanges(key.key(), RECENT_CHANGES));
    }

    /** 管理后台：切换（记录操作人、时间、原因），本实例缓存立即失效。 */
    @Transactional(rollbackFor = Exception.class)
    public SwitchView set(Long operatorId, String switchKey, boolean enabled, String reason) {
        FeatureKey key = FeatureKey.ofKey(switchKey).orElseThrow(() -> new BizException("未知的功能开关"));
        if (!enabled && (reason == null || reason.isBlank())) throw new BizException("关停" + key.label() + "请填写原因");
        FeatureSwitch next = FeatureSwitch.change(key.key(), enabled, operatorId, operatorName(operatorId),
                LocalDateTime.now(clock), reason);
        repository.save(next);
        // 提交后再失效缓存：避免并发请求在提交前读到旧值又缓存一个 TTL
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.remove(key.key());
                }
            });
        }
        cache.remove(key.key());
        log.warn("开关 {} 切换为 {}，操作人 {}({})，原因：{}", key.key(), enabled ? "开" : "关", next.updatedByName(),
                operatorId, next.reason());
        return view(key);
    }

    private String operatorName(Long operatorId) {
        AccountQueryApi api = accounts.getIfAvailable();
        if (api == null) return null;
        try {
            var v = api.views(List.of(operatorId)).get(operatorId);
            return v == null ? null : v.nickname();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
