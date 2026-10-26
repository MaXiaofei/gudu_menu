package com.gudu.xsd.modules.family.domain.event;

import com.gudu.xsd.common.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** 账号加入家庭（含建家时的 OWNER）。application 监听：为该账号在家庭内建立关联成员档案。 */
public record MemberJoined(String eventId, Instant occurredOn, Long familyId, Long accountId) implements DomainEvent {

    public MemberJoined(Long familyId, Long accountId) {
        this(UUID.randomUUID().toString(), Instant.now(), familyId, accountId);
    }
}
