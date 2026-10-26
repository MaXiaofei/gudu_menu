package com.gudu.xsd.modules.family.domain.model;

import java.util.Locale;

/** 家庭内角色：主理人（恰好一个）/ 管理员 / 成员。rank 越大权限越高。 */
public enum FamilyRole {
    MEMBER(1), ADMIN(2), OWNER(3);

    private final int rank;

    FamilyRole(int rank) {
        this.rank = rank;
    }

    public boolean higherThan(FamilyRole other) {
        return rank > other.rank;
    }

    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }

    public static FamilyRole of(String s) {
        return valueOf(s.trim().toUpperCase(Locale.ROOT));
    }
}
