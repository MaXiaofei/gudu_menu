package com.gudu.xsd.modules.ai.gateway.domain.service;

import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelSpec;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 把路由解析成「可执行目标」有序列表（纯函数）：
 * 跳过停用 / 不存在 / 模型不支持该能力 / 缺 key 的目标，并记录跳过原因；Mock 永远在最后（若链里没有则补上）。
 */
public final class RouteChainResolver {

    private RouteChainResolver() {
    }

    /** 可执行目标。provider 为 null 表示「规则兜底」。 */
    public record Candidate(ModelProvider provider, ModelSpec spec) {

        public boolean isRule() {
            return provider == null || provider.isMock();
        }

        public String providerName() {
            return provider == null ? "mock" : provider.name();
        }

        public String modelName() {
            return spec == null ? "mock" : spec.model();
        }
    }

    public record Resolution(List<Candidate> candidates, List<String> skipped) {
    }

    /**
     * @param hasKey 供应商是否拿得到 key（DB 密文可解 / ENV 有值）
     */
    public static Resolution resolve(ModelRoute route, Map<Long, ModelProvider> providers,
                                     Predicate<ModelProvider> hasKey) {
        Capability cap = route.capability();
        List<Candidate> out = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (RouteTarget t : route.chain()) {
            ModelProvider p = providers.get(t.providerId());
            if (p == null) {
                skipped.add("供应商 " + t.providerId() + " 不存在");
                continue;
            }
            if (!p.enabled()) {
                skipped.add(p.name() + " 已停用");
                continue;
            }
            if (p.isMock()) {
                out.add(new Candidate(p, p.model(t.model()).orElse(null)));
                break; // 规则兜底之后的目标没有意义
            }
            ModelSpec spec = p.model(t.model()).orElse(null);
            if (spec == null) {
                skipped.add(p.name() + "/" + t.model() + " 模型不存在");
                continue;
            }
            if (!spec.supports(cap)) {
                skipped.add(p.name() + "/" + t.model() + " 不支持 " + cap);
                continue;
            }
            if (!hasKey.test(p)) {
                skipped.add(p.name() + " 未配置 key");
                continue;
            }
            out.add(new Candidate(p, spec));
        }
        if (out.isEmpty() || !out.get(out.size() - 1).isRule()) {
            out.add(new Candidate(null, null));
        }
        return new Resolution(out, skipped);
    }
}
