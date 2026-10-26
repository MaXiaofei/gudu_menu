package com.gudu.xsd.modules.identity.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

/**
 * 账号注册完成（已分配 id）。family 上下文消费：凭邀请码注册则加入目标家庭；
 * 否则 {@code autoCreateFamily=true} 时自动建「我的家」并成为 OWNER
 * （超管代建账号并直接加入某家庭时为 false，由调用方随后加入）。
 *
 * @param inviteCode 注册时携带的家庭邀请码（可空）
 */
public final class AccountRegistered extends AbstractDomainEvent {

    private final Long accountId;
    private final String nickname;
    private final String inviteCode;
    private final boolean autoCreateFamily;

    public AccountRegistered(Long accountId, String nickname, String inviteCode, boolean autoCreateFamily) {
        this.accountId = accountId;
        this.nickname = nickname;
        this.inviteCode = inviteCode;
        this.autoCreateFamily = autoCreateFamily;
    }

    public Long accountId() {
        return accountId;
    }

    public String nickname() {
        return nickname;
    }

    public String inviteCode() {
        return inviteCode;
    }

    public boolean autoCreateFamily() {
        return autoCreateFamily;
    }
}
