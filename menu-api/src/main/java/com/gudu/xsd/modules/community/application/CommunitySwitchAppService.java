package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.application.dto.CommunityViews.UserLite;
import com.gudu.xsd.modules.community.domain.model.CommunitySwitch;
import com.gudu.xsd.modules.community.domain.repository.CommunitySwitchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 厨房圈紧急关停开关（mini-release-compliance A.12）。
 *
 * <p>只用于出现内容风险时一键关闭（不是审核后开放的开关；小程序端按 A.1 构建期不打包，与本开关无关）。
 * 读路径（每个社区请求都会问）走进程内短缓存 {@link #CACHE_TTL}：本实例切换立即生效，
 * 其他实例最迟 TTL 后生效（≤30 秒）。读库失败时沿用上次已知值（从未读到则视为开）。
 */
@Slf4j
@Service
public class CommunitySwitchAppService {

    public static final Duration CACHE_TTL = Duration.ofSeconds(15);
    public static final int RECENT_CHANGES = 10;

    private final CommunitySwitchRepository repository;
    private final CommunityAssembler assembler;
    private final Clock clock;

    private volatile Cached cached;

    public CommunitySwitchAppService(CommunitySwitchRepository repository, CommunityAssembler assembler, Clock clock) {
        this.repository = repository;
        this.assembler = assembler;
        this.clock = clock;
    }

    private record Cached(boolean enabled, Instant expiresAt) {
    }

    /** 当前与最近修改记录（管理后台展示）。 */
    public record SwitchView(boolean enabled, Long updatedBy, String updatedByName, LocalDateTime updatedAt,
                             String reason, List<CommunitySwitch> recentChanges) {
    }

    /** 厨房圈是否开放（带短缓存，社区接口拦截用）。 */
    public boolean isEnabled() {
        Instant now = clock.instant();
        Cached c = cached;
        if (c != null && now.isBefore(c.expiresAt())) return c.enabled();
        boolean enabled;
        try {
            enabled = repository.load().map(CommunitySwitch::enabled).orElse(true);
        } catch (RuntimeException e) {
            enabled = c == null || c.enabled();
            log.warn("读取厨房圈开关失败，沿用 {}：{}", enabled ? "开" : "关", e.getMessage());
        }
        cached = new Cached(enabled, now.plus(CACHE_TTL));
        return enabled;
    }

    /** 管理后台：当前状态（不走缓存）+ 最近修改记录。 */
    public SwitchView view() {
        CommunitySwitch s = repository.load().orElse(CommunitySwitch.defaultOn());
        return new SwitchView(s.enabled(), s.updatedBy(), s.updatedByName(), s.updatedAt(), s.reason(),
                repository.recentChanges(RECENT_CHANGES));
    }

    /** 管理后台：切换（记录操作人、时间、原因），本实例缓存立即失效。 */
    @Transactional(rollbackFor = Exception.class)
    public SwitchView set(Long operatorId, boolean enabled, String reason) {
        Map<Long, UserLite> users = assembler.users(List.of(operatorId));
        UserLite op = users.get(operatorId);
        CommunitySwitch next = CommunitySwitch.change(enabled, operatorId, op == null ? null : op.nickname(),
                LocalDateTime.now(clock), reason);
        repository.save(next);
        // 提交后再失效缓存：避免并发请求在提交前读到旧值又缓存一个 TTL
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cached = null;
                }
            });
        }
        cached = null;
        log.warn("厨房圈开关切换为 {}，操作人 {}({})，原因：{}", enabled ? "开" : "关", next.updatedByName(),
                operatorId, next.reason());
        return view();
    }
}
