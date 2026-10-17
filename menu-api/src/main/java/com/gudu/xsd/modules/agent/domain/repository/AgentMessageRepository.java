package com.gudu.xsd.modules.agent.domain.repository;

import com.gudu.xsd.modules.agent.domain.model.AgentMessage;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 消息仓储。 */
public interface AgentMessageRepository {

    void save(AgentMessage message);

    Optional<AgentMessage> findById(String id);

    void updateRating(String messageId, int rating, String comment);

    /** 动作状态变化后同步消息里的快照。 */
    void updateAction(String messageId, PendingAction action);

    /** 倒序分页（游标 = 上一页最后一条的 (createdAt, id)）。 */
    List<AgentMessage> listBySession(String sessionId, LocalDateTime beforeCreatedAt, String beforeId, int size);

    /** 最近 n 条（正序），组装模型上下文用。 */
    List<AgentMessage> recent(String sessionId, int n);

    /** 会话全部消息（正序）。 */
    List<AgentMessage> all(String sessionId);
}
