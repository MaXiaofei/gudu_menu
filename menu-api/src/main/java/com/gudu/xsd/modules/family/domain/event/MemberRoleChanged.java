package com.gudu.xsd.modules.family.domain.event;

import com.gudu.xsd.common.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** 成员角色变更（OWNER 调整 ADMIN/MEMBER）。 */
public record MemberRoleChanged(String eventId, Instant occurredOn, Long familyId, Long accountId, String role) implements DomainEvent {

    public MemberRoleChanged(Long familyId, Long accountId, String role) {
        this(UUID.randomUUID().toString(), Instant.now(), familyId, accountId, role);
    }
}
