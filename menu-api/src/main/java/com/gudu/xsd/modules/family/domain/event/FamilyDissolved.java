package com.gudu.xsd.modules.family.domain.event;

import com.gudu.xsd.common.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** 家庭已解散（I4：此后拒绝一切变更；家庭数据保留不删）。 */
public record FamilyDissolved(String eventId, Instant occurredOn, Long familyId) implements DomainEvent {

    public FamilyDissolved(Long familyId) {
        this(UUID.randomUUID().toString(), Instant.now(), familyId);
    }
}
