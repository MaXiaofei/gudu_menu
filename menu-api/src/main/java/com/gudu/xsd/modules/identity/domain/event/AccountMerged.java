package com.gudu.xsd.modules.identity.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

/**
 * 微信账号 source 已并入手机号账号 target（design §3.1 账号合并流程）。
 * 各上下文同事务消费，把按账号归属的数据从 source 改指 target：
 * family（成员关系、成员档案）、agent（会话/消息/轨迹）、feedback、vision、mealplan 草稿、community。
 */
public final class AccountMerged extends AbstractDomainEvent {

    private final Long sourceAccountId;
    private final Long targetAccountId;

    public AccountMerged(Long sourceAccountId, Long targetAccountId) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
    }

    public Long sourceAccountId() {
        return sourceAccountId;
    }

    public Long targetAccountId() {
        return targetAccountId;
    }
}
