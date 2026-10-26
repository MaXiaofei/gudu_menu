package com.gudu.xsd.modules.family.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.Objects;

/** 家庭邀请（Family 聚合内值对象，不可变）：7 天有效、可撤销、有使用上限。 */
public record Invitation(InviteCode code, Long createdBy, LocalDateTime expiresAt, int maxUses, int usedCount,
                         boolean revoked, LocalDateTime createdAt) {

    public Invitation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (maxUses < 1) throw new DomainException("邀请可用次数至少 1 次");
    }

    /** 加入前校验：撤销 / 过期 / 用尽 → 拒绝。 */
    public void ensureUsable(LocalDateTime now) {
        if (revoked) throw new DomainException("邀请已失效");
        if (!now.isBefore(expiresAt)) throw new DomainException("邀请已过期");
        if (usedCount >= maxUses) throw new DomainException("邀请已用完");
    }

    public Invitation used() {
        return new Invitation(code, createdBy, expiresAt, maxUses, usedCount + 1, revoked, createdAt);
    }

    public Invitation revoke() {
        return new Invitation(code, createdBy, expiresAt, maxUses, usedCount, true, createdAt);
    }
}
