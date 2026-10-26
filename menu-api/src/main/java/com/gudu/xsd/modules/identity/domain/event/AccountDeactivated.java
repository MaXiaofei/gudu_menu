package com.gudu.xsd.modules.identity.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

/** 账号已注销（已匿名化）。family 上下文消费：退出全部家庭、匿名化其成员档案。 */
public final class AccountDeactivated extends AbstractDomainEvent {

    private final Long accountId;

    public AccountDeactivated(Long accountId) {
        this.accountId = accountId;
    }

    public Long accountId() {
        return accountId;
    }
}
