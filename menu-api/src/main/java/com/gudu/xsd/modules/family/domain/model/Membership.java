package com.gudu.xsd.modules.family.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** 家庭成员关系（Family 聚合内值对象，不可变；角色变更产生新实例）。 */
public record Membership(Long accountId, FamilyRole role, LocalDateTime joinedAt) {

    public Membership {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(role, "role");
    }

    public Membership withRole(FamilyRole newRole) {
        return new Membership(accountId, newRole, joinedAt);
    }

    public Membership withAccount(Long newAccountId) {
        return new Membership(newAccountId, role, joinedAt);
    }
}
