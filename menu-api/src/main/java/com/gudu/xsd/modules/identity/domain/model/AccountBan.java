package com.gudu.xsd.modules.identity.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 账号封禁（mini-release-compliance F.1）：独立小聚合，以账号 id 为标识；存在 = 封禁中，解封即删除
 * （历史由运营操作日志追溯）。不放进 Account 聚合：封禁是平台治理动作，与凭证 / 合并 / 注销的生命周期无关。
 *
 * <p>规则：原因 1–200 字；不能封禁自己；超级管理员不可被封禁；平台运营只能由超级管理员封禁。
 */
public final class AccountBan extends AggregateRoot<Long> {

    static final int REASON_MAX = 200;

    private final Long accountId;
    private final String reason;
    private final Long bannedBy;
    private final LocalDateTime bannedAt;

    private AccountBan(Long accountId, String reason, Long bannedBy, LocalDateTime bannedAt) {
        this.accountId = accountId;
        this.reason = reason;
        this.bannedBy = bannedBy;
        this.bannedAt = bannedAt;
    }

    /**
     * 封禁。
     *
     * @param targetRole   被封账号的平台角色
     * @param operatorRole 操作人的平台角色
     */
    public static AccountBan impose(Long accountId, PlatformRole targetRole, Long operatorId,
                                    PlatformRole operatorRole, String reason, LocalDateTime now) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(operatorId, "operatorId");
        if (accountId.equals(operatorId)) throw new DomainException("不能封禁自己");
        if (targetRole == PlatformRole.SUPER_ADMIN) throw new DomainException("超级管理员不可封禁");
        if (targetRole == PlatformRole.OPERATOR && operatorRole != PlatformRole.SUPER_ADMIN) {
            throw new DomainException("平台运营账号只能由超级管理员封禁");
        }
        return new AccountBan(accountId, checkReason(reason), operatorId, now);
    }

    public static AccountBan rehydrate(Long accountId, String reason, Long bannedBy, LocalDateTime bannedAt) {
        return new AccountBan(accountId, reason, bannedBy, bannedAt);
    }

    /** 解封、恢复等操作的原因校验（同样 1–200 字）。 */
    public static String checkReason(String reason) {
        String v = reason == null ? "" : reason.trim();
        if (v.isEmpty()) throw new DomainException("请填写原因");
        if (v.length() > REASON_MAX) throw new DomainException("原因不超过 200 字");
        return v;
    }

    @Override
    public Long id() {
        return accountId;
    }

    public Long accountId() {
        return accountId;
    }

    public String reason() {
        return reason;
    }

    public Long bannedBy() {
        return bannedBy;
    }

    public LocalDateTime bannedAt() {
        return bannedAt;
    }
}
