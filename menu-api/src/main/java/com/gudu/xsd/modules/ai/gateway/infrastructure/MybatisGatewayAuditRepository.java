package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayAuditRepository;
import com.gudu.xsd.modules.ai.gateway.infrastructure.mapper.AiGatewayAuditMapper;
import com.gudu.xsd.modules.ai.gateway.infrastructure.po.AiGatewayAuditPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 网关配置审计（ai_gateway_audit）。 */
@Repository
@RequiredArgsConstructor
public class MybatisGatewayAuditRepository implements GatewayAuditRepository {

    private final AiGatewayAuditMapper mapper;

    @Override
    public void append(String actor, String action, String target, String detailJson) {
        AiGatewayAuditPO po = new AiGatewayAuditPO();
        po.setActor(actor);
        po.setAction(action);
        po.setTarget(target);
        po.setDetail(detailJson);
        mapper.insert(po);
    }
}
