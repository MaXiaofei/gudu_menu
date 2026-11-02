package com.gudu.xsd.modules.ai.gateway.domain.port;

/** 全站每日花费闸门：已达上限则网关不再调用真实模型（走规则兜底或抛额度用完）。实现在 aiguard 模块。 */
public interface AiBudgetGuard {

    /** 不设限（单测 / 评测夹具用）。 */
    AiBudgetGuard NONE = () -> false;

    /** 今日额度是否已用完。 */
    boolean exhausted();
}
