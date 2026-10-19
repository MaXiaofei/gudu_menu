package com.gudu.xsd.modules.agent.domain.repository;

import com.gudu.xsd.modules.agent.domain.model.AgentSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 会话仓储。 */
public interface AgentSessionRepository {

    void save(AgentSession session);

    Optional<AgentSession> findById(String id);

    /** 按 updatedAt 倒序；游标为上一页最后一条的 (updatedAt, id)。 */
    List<AgentSession> listByMember(Long memberId, LocalDateTime beforeUpdatedAt, String beforeId, int size);
}
