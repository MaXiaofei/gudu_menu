package com.gudu.xsd.modules.identity.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

/** 账号已注销（已匿名化）。family 上下文消费：退出全部家庭、匿名化其成员档案。 */
public final class AccountDeactivated extends AbstractDomainEvent {

    private final Long accountId;
    /** 注销前账号上的头像地址（匿名化会清空该字段，头像文件删除要靠它）；可空。 */
    private final String avatarUrl;

    public AccountDeactivated(Long accountId) {
        this(accountId, null);
    }

    public AccountDeactivated(Long accountId, String avatarUrl) {
        this.accountId = accountId;
        this.avatarUrl = avatarUrl;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public Long accountId() {
        return accountId;
    }
}
