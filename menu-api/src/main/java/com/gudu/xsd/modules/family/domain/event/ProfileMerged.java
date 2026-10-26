package com.gudu.xsd.modules.family.domain.event;

import com.gudu.xsd.common.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** 账号合并时同家庭重复档案并入目标档案：按档案归属的个人数据（AI 对话等）应由 from 改指 to。 */
public record ProfileMerged(String eventId, Instant occurredOn, Long fromProfileId, Long toProfileId)
        implements DomainEvent {

    public ProfileMerged(Long fromProfileId, Long toProfileId) {
        this(UUID.randomUUID().toString(), Instant.now(), fromProfileId, toProfileId);
    }
}
