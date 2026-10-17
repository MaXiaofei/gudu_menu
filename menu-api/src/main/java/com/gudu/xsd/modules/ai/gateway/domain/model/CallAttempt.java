package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.math.BigDecimal;

/**
 * 网关的一次尝试（每次重试 / 每个降级目标各一条），写入调用日志。
 *
 * @param error 失败原因（已脱敏，不含 key）
 */
public record CallAttempt(String provider, String model, boolean ok, String error, long ms, TokenUsage usage,
                          BigDecimal costFen) {
}
