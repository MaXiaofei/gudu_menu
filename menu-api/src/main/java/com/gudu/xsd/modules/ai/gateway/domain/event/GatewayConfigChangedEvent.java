package com.gudu.xsd.modules.ai.gateway.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

/** 网关配置已变更（本实例或其他实例经 Redis 广播）：收到后失效本地路由缓存。 */
public final class GatewayConfigChangedEvent extends AbstractDomainEvent {

    private final String reason;

    public GatewayConfigChangedEvent(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
