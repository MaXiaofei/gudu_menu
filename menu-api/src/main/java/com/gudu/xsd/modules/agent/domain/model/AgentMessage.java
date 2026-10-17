package com.gudu.xsd.modules.agent.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话中的一条消息。assistant 消息带工具记录、卡片、待确认动作快照；reasoning 为思考内容（思考模式下回传模型）。
 *
 * @param cards   卡片（{type, ...}，结构见 contracts.md AgentCard）
 * @param actions 待确认动作快照（状态变化时同步更新；Redis 过期后仍可展示）
 */
public record AgentMessage(String messageId, String sessionId, Long memberId, String role, String text,
                           List<ToolCallRecord> tools, List<Map<String, Object>> cards, List<PendingAction> actions,
                           String traceId, String reasoning, Integer rating, LocalDateTime createdAt) {

    public AgentMessage {
        tools = tools == null ? List.of() : List.copyOf(tools);
        cards = cards == null ? List.of() : List.copyOf(cards);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static AgentMessage user(String id, String sessionId, Long memberId, String text, LocalDateTime at) {
        return new AgentMessage(id, sessionId, memberId, "user", text, List.of(), List.of(), List.of(), null, null,
                null, at);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }
}
