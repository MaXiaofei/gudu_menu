package com.gudu.xsd.modules.family.domain.event;

import com.gudu.xsd.common.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** 账号离开家庭（主动退出 / 被移除 / 注销 / 合并转移）。application 监听：失效角色缓存。 */
public record MemberLeft(String eventId, Instant occurredOn, Long familyId, Long accountId) implements DomainEvent {

    public MemberLeft(Long familyId, Long accountId) {
        this(UUID.randomUUID().toString(), Instant.now(), familyId, accountId);
    }
}
