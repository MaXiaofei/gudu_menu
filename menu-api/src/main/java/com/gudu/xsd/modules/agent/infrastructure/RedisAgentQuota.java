package com.gudu.xsd.modules.agent.infrastructure;

import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.common.security.AiQuotaGate;
import com.gudu.xsd.modules.agent.domain.repository.AgentQuota;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 咕嘟每日对话轮数：委托统一的 AI 次数闸门（类别 AGENT，按登录账号计，Redis 故障拒绝）。
 * 返回 false 时对话退回规则回复（不调模型），不是报错。
 */
@Component
@RequiredArgsConstructor
public class RedisAgentQuota implements AgentQuota {

    private final AiQuotaGate gate;

    @Override
    public boolean consume(Long accountId, int defaultLimit) {
        return gate.tryConsume(AiQuotaCategory.AGENT, accountId);
    }
}
