package com.gudu.xsd.common.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 领域事件基类：生成 eventId + occurredOn，子类只放业务字段（全部 final，不可变）。 */
public abstract class AbstractDomainEvent implements DomainEvent {

    private final String eventId;
    private final Instant occurredOn;

    protected AbstractDomainEvent() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    protected AbstractDomainEvent(String eventId, Instant occurredOn) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn");
    }

    @Override
    public String eventId() {
        return eventId;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + eventId + "]";
    }
}
