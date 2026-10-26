package com.gudu.xsd.modules.family.domain.event;

import com.gudu.xsd.common.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** 家庭已创建（创建人成为 OWNER）。 */
public record FamilyCreated(String eventId, Instant occurredOn, Long familyId, Long ownerAccountId) implements DomainEvent {

    public FamilyCreated(Long familyId, Long ownerAccountId) {
        this(UUID.randomUUID().toString(), Instant.now(), familyId, ownerAccountId);
    }
}
