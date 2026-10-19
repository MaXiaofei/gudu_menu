package com.gudu.xsd.modules.ai.gateway.domain.port;

/** 配置变更广播（Redis pub/sub）：各实例收到后失效本地缓存。 */
public interface GatewayChangeBus {

    void publishChanged(String reason);
}
