package com.gudu.xsd.modules.family.domain.event;

import com.gudu.xsd.common.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** 主理人转让：原 OWNER 降为 ADMIN。 */
public record OwnershipTransferred(String eventId, Instant occurredOn, Long familyId, Long fromAccountId, Long toAccountId) implements DomainEvent {

    public OwnershipTransferred(Long familyId, Long fromAccountId, Long toAccountId) {
        this(UUID.randomUUID().toString(), Instant.now(), familyId, fromAccountId, toAccountId);
    }
}
