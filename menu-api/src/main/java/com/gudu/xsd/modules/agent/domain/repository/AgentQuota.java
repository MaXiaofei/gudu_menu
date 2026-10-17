package com.gudu.xsd.modules.agent.domain.repository;

/** 每人每日对话配额。超额不报错，降级为规则模式。 */
public interface AgentQuota {

    /** 计一次并返回是否仍在额度内（limit ≤ 0 = 不限）。 */
    boolean consume(Long memberId, int limit);
}
