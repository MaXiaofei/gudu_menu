package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.time.LocalDateTime;

/** 最近一次连通性检测结果。 */
public record ProviderHealth(boolean ok, Integer latencyMs, LocalDateTime checkedAt) {

    public static final ProviderHealth UNKNOWN = new ProviderHealth(false, null, null);
}
