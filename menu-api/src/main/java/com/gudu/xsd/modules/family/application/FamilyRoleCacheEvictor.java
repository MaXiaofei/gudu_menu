package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.modules.family.domain.event.FamilyDissolved;
import com.gudu.xsd.modules.family.domain.event.MemberJoined;
import com.gudu.xsd.modules.family.domain.event.MemberLeft;
import com.gudu.xsd.modules.family.domain.event.MemberRoleChanged;
import com.gudu.xsd.modules.family.domain.event.OwnershipTransferred;
import com.gudu.xsd.modules.family.domain.repository.FamilyRoleCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 成员关系变化 → 提交后失效角色缓存（AFTER_COMMIT：避免并发请求在提交前把旧角色回填进缓存）。
 * 无事务时（fallbackExecution）立即失效。被移出的成员下一次请求即 403 FAMILY_ACCESS_DENIED。
 */
@Component
@RequiredArgsConstructor
public class FamilyRoleCacheEvictor {

    private final FamilyRoleCache cache;

    @TransactionalEventListener(fallbackExecution = true)
    public void on(MemberLeft e) {
        cache.evictFamily(e.familyId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(MemberJoined e) {
        cache.evictFamily(e.familyId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(MemberRoleChanged e) {
        cache.evictFamily(e.familyId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(OwnershipTransferred e) {
        cache.evictFamily(e.familyId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(FamilyDissolved e) {
        cache.evictFamily(e.familyId());
    }
}
