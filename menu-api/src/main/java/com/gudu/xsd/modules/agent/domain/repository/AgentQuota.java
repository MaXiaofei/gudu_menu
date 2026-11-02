package com.gudu.xsd.modules.agent.domain.repository;

/** 每登录账号每日对话配额。超额（或计数设施故障）不报错，降级为规则模式。 */
public interface AgentQuota {

    /**
     * 计一次并返回是否仍在额度内。按登录账号（account.id）计；实际上限以后台「AI 用量与额度」为准，
     * {@code defaultLimit} 只是应用配置里的默认值（后台没改过时一致）。
     */
    boolean consume(Long accountId, int defaultLimit);
}
