package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网关结果：最终生效的回复 + 实际供应商/模型 + 成本 + 全部尝试。
 *
 * @param degraded 是否未用主目标（降级到后备模型或规则）
 */
public record ChatResult(ModelReply reply, String provider, String model, boolean degraded, long latencyMs,
                         BigDecimal costFen, List<CallAttempt> attempts) {

    public ChatResult {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        costFen = costFen == null ? BigDecimal.ZERO : costFen;
    }

    public String content() {
        return reply.content();
    }

    public boolean byRule() {
        return "mock".equalsIgnoreCase(provider);
    }
}
