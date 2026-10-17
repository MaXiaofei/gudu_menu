package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.util.List;

/** 单个模型一次调用的原始回复（传输层输出）。 */
public record ModelReply(String content, String reasoningContent, List<ToolCall> toolCalls, TokenUsage usage) {

    public ModelReply {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? TokenUsage.ZERO : usage;
    }

    public static ModelReply text(String content) {
        return new ModelReply(content, null, List.of(), TokenUsage.ZERO);
    }

    public static ModelReply calls(List<ToolCall> calls) {
        return new ModelReply(null, null, calls, TokenUsage.ZERO);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
