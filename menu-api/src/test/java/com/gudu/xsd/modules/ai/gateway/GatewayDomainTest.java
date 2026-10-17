package com.gudu.xsd.modules.ai.gateway;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.ai.gateway.application.GatewayDefaults;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelPrice;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.PeakHours;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.model.TokenUsage;
import com.gudu.xsd.modules.ai.gateway.domain.service.CostCalculator;
import com.gudu.xsd.modules.ai.gateway.domain.service.RouteChainResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayDomainTest {

    @Test
    void DeepSeek高峰时段_工作日9到12_14到18_北京时间() {
        PeakHours p = PeakHours.deepSeek();
        assertThat(p.isPeak(Instant.parse("2026-10-16T01:00:00Z"))).as("周五 09:00").isTrue();
        assertThat(p.isPeak(Instant.parse("2026-10-16T04:30:00Z"))).as("周五 12:30 午休").isFalse();
        assertThat(p.isPeak(Instant.parse("2026-10-16T09:59:00Z"))).as("周五 17:59").isTrue();
        assertThat(p.isPeak(Instant.parse("2026-10-16T10:00:00Z"))).as("周五 18:00").isFalse();
        assertThat(p.isPeak(Instant.parse("2026-10-17T02:00:00Z"))).as("周六 10:00").isFalse();
    }

    @Test
    void 成本_缓存命中与未命中分开计价_非高峰半价() {
        ModelPrice flash = new ModelPrice(new BigDecimal("4"), new BigDecimal("200"), new BigDecimal("800"));
        TokenUsage u = new TokenUsage(2000, 1500, 300);
        // 1500×4 + 500×200 + 300×800 = 6000 + 100000 + 240000 = 346000 / 1e6 = 0.346 分
        assertThat(CostCalculator.costFen(flash, PeakHours.deepSeek(), u, Instant.parse("2026-10-16T02:00:00Z")))
                .isEqualByComparingTo("0.346");
        assertThat(CostCalculator.costFen(flash, PeakHours.deepSeek(), u, Instant.parse("2026-10-16T13:00:00Z")))
                .isEqualByComparingTo("0.173");
        assertThat(CostCalculator.costFen(flash, null, u, Instant.now())).isEqualByComparingTo("0.346");
    }

    @Test
    void 路由不变量() {
        RouteTarget a = new RouteTarget(1L, "m");
        assertThatThrownBy(() -> ModelRoute.restore(Capability.CHAT, a, List.of(a), 30_000, 1, ThinkingMode.DISABLED,
                null, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ModelRoute.restore(Capability.CHAT, a, List.of(), 500, 1, ThinkingMode.DISABLED,
                null, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ModelRoute.restore(Capability.CHAT, a, List.of(), 30_000, 4, ThinkingMode.DISABLED,
                null, null)).isInstanceOf(DomainException.class);
    }

    @Test
    void 路由解析_跳过停用和不支持的_末尾补规则兜底() {
        Map<Long, ModelProvider> ps = GatewayDefaults.providers().stream()
                .collect(Collectors.toMap(ModelProvider::id, Function.identity()));
        ModelRoute vision = GatewayDefaults.routes().get(2);
        RouteChainResolver.Resolution r = RouteChainResolver.resolve(vision, ps, p -> true);
        assertThat(r.candidates()).extracting(RouteChainResolver.Candidate::modelName)
                .containsExactly("deepseek-flash", "mock");
        assertThat(r.skipped()).anyMatch(s -> s.contains("GLM 已停用"));
        ModelRoute badModel = ModelRoute.restore(Capability.VISION, new RouteTarget(GatewayDefaults.DEEPSEEK_ID,
                GatewayDefaults.DEEPSEEK_PRO), List.of(), 30_000, 0, ThinkingMode.DISABLED, null, null);
        RouteChainResolver.Resolution r2 = RouteChainResolver.resolve(badModel, ps, p -> true);
        assertThat(r2.candidates()).hasSize(1).allMatch(RouteChainResolver.Candidate::isRule);
        assertThat(r2.skipped()).anyMatch(s -> s.contains("不支持 VISION"));
    }
}
