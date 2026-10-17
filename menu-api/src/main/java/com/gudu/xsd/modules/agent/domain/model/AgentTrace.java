package com.gudu.xsd.modules.agent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一轮对话的追踪头。
 *
 * @param phoneMasked 本轮脱敏的手机号个数
 * @param feedback    用户评价 1 / -1 / null
 */
public record AgentTrace(String traceId, String sessionId, String messageId, Long memberId, String firstText,
                         TraceStatus status, String provider, String model, String promptVersion, boolean degraded,
                         long ms, Long firstTokenMs, int promptTokens, int completionTokens, BigDecimal costFen,
                         int phoneMasked, Integer feedback, LocalDateTime createdAt) {

    public int tokens() {
        return promptTokens + completionTokens;
    }
}
