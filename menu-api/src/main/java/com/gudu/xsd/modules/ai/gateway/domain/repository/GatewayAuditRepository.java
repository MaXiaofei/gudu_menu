package com.gudu.xsd.modules.ai.gateway.domain.repository;

/** 网关配置变更审计（谁、何时、改了什么；detail 不含 key 明文）。 */
public interface GatewayAuditRepository {

    void append(String actor, String action, String target, String detailJson);
}
