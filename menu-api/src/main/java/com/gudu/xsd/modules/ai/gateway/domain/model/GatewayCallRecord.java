package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 网关调用日志的一条（每次尝试一条）。 */
public record GatewayCallRecord(Capability capability, String scene, String provider, String model, boolean ok,
                                String error, long ms, TokenUsage usage, BigDecimal costFen, String traceId,
                                Long memberId, LocalDateTime at) {
}
