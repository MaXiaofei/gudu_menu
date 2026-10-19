package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * 一轮对话的输入。
 *
 * @param history   之前的轮次（user / assistant 文本，assistant 可带 reasoning），不含 system
 * @param context   客户端上下文（page / dishId / step）
 * @param forceRule 直接走规则（配额用尽 / 评测 mock）
 */
public record TurnRequest(String sessionId, String messageId, String traceId, Long memberId, String text,
                          Map<String, Object> context, List<ChatMessage> history, boolean forceRule) {

    public TurnRequest {
        history = history == null ? List.of() : List.copyOf(history);
        context = context == null ? Map.of() : context;
    }
}
