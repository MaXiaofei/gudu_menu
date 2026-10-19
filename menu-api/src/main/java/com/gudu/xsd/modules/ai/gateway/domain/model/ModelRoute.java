package com.gudu.xsd.modules.ai.gateway.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 能力路由（聚合根，标识 = 能力）：主目标 + 有序降级链 + 超时 + 重试 + 思考模式。
 *
 * <p>不变量：降级链不含主目标、不重复、最多 5 个；超时 1s–120s；重试 0–3 次。
 */
public class ModelRoute extends AggregateRoot<Capability> {

    public static final int MAX_FALLBACKS = 5;
    public static final int MIN_TIMEOUT_MS = 1_000;
    public static final int MAX_TIMEOUT_MS = 120_000;
    public static final int MAX_RETRIES = 3;

    private final Capability capability;
    private RouteTarget primary;
    private List<RouteTarget> fallbacks;
    private int timeoutMs;
    private int maxRetries;
    private ThinkingMode thinking;
    private LocalDateTime updatedAt;
    private String updatedBy;

    private ModelRoute(Capability capability, RouteTarget primary, List<RouteTarget> fallbacks, int timeoutMs,
                       int maxRetries, ThinkingMode thinking, LocalDateTime updatedAt, String updatedBy) {
        if (capability == null) throw new DomainException("路由能力不能为空");
        this.capability = capability;
        apply(primary, fallbacks, timeoutMs, maxRetries, thinking);
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public static ModelRoute restore(Capability capability, RouteTarget primary, List<RouteTarget> fallbacks,
                                     int timeoutMs, int maxRetries, ThinkingMode thinking,
                                     LocalDateTime updatedAt, String updatedBy) {
        return new ModelRoute(capability, primary, fallbacks, timeoutMs, maxRetries, thinking, updatedAt, updatedBy);
    }

    @Override
    public Capability id() {
        return capability;
    }

    /** 重新配置（管理后台）。 */
    public void reconfigure(RouteTarget newPrimary, List<RouteTarget> newFallbacks, int newTimeoutMs,
                            int newMaxRetries, ThinkingMode newThinking, String operator, LocalDateTime at) {
        apply(newPrimary, newFallbacks, newTimeoutMs, newMaxRetries, newThinking);
        this.updatedBy = operator;
        this.updatedAt = at;
    }

    /** 只换主目标（兼容旧 PUT /ai/provider）：原主目标若在降级链里则移除新主目标，避免重复。 */
    public void switchPrimary(RouteTarget newPrimary, String operator, LocalDateTime at) {
        List<RouteTarget> fb = new ArrayList<>(fallbacks);
        fb.remove(newPrimary);
        reconfigure(newPrimary, fb, timeoutMs, maxRetries, thinking, operator, at);
    }

    private void apply(RouteTarget newPrimary, List<RouteTarget> newFallbacks, int newTimeoutMs, int newMaxRetries,
                       ThinkingMode newThinking) {
        if (newPrimary == null) throw new DomainException("必须指定主模型");
        List<RouteTarget> fb = newFallbacks == null ? List.of() : newFallbacks;
        if (fb.size() > MAX_FALLBACKS) throw new DomainException("降级链最多 " + MAX_FALLBACKS + " 个");
        LinkedHashSet<RouteTarget> seen = new LinkedHashSet<>();
        seen.add(newPrimary);
        for (RouteTarget t : fb) {
            if (!seen.add(t)) throw new DomainException("降级链重复或与主模型相同：" + t.model());
        }
        if (newTimeoutMs < MIN_TIMEOUT_MS || newTimeoutMs > MAX_TIMEOUT_MS) {
            throw new DomainException("超时需在 " + MIN_TIMEOUT_MS + "–" + MAX_TIMEOUT_MS + " ms 之间");
        }
        if (newMaxRetries < 0 || newMaxRetries > MAX_RETRIES) {
            throw new DomainException("重试次数需在 0–" + MAX_RETRIES + " 之间");
        }
        this.primary = newPrimary;
        this.fallbacks = List.copyOf(fb);
        this.timeoutMs = newTimeoutMs;
        this.maxRetries = newMaxRetries;
        this.thinking = newThinking == null ? ThinkingMode.DISABLED : newThinking;
    }

    /** 主目标 + 降级链（有序）。 */
    public List<RouteTarget> chain() {
        List<RouteTarget> out = new ArrayList<>(fallbacks.size() + 1);
        out.add(primary);
        out.addAll(fallbacks);
        return out;
    }

    /** 是否引用了该供应商（删除供应商前校验）。 */
    public boolean references(Long providerId) {
        return chain().stream().anyMatch(t -> t.providerId().equals(providerId));
    }

    public Capability capability() {
        return capability;
    }

    public RouteTarget primary() {
        return primary;
    }

    public List<RouteTarget> fallbacks() {
        return fallbacks;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public ThinkingMode thinking() {
        return thinking;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public String updatedBy() {
        return updatedBy;
    }
}
