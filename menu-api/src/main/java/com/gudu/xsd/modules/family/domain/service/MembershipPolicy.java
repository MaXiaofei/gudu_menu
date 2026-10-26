package com.gudu.xsd.modules.family.domain.service;

import com.gudu.xsd.common.domain.DomainException;

/**
 * 跨聚合规则：每个账号最多加入 {@value #MAX_FAMILIES_PER_ACCOUNT} 个（未解散的）家庭。
 * 应用层在 create / join 前查询 {@code FamilyRepository.countActiveByAccount} 后调用。
 */
public final class MembershipPolicy {

    public static final int MAX_FAMILIES_PER_ACCOUNT = 5;

    private MembershipPolicy() {
    }

    public static void ensureCanJoinAnother(long activeFamilyCount) {
        if (activeFamilyCount >= MAX_FAMILIES_PER_ACCOUNT) {
            throw new DomainException("每个账号最多加入 " + MAX_FAMILIES_PER_ACCOUNT + " 个家庭");
        }
    }
}
