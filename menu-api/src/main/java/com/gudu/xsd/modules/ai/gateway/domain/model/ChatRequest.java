package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.util.List;

/**
 * 网关请求。
 *
 * @param capability   路由能力
 * @param scene        业务场景（agent / nutrition_fill / vision …），用于调用日志与用量统计
 * @param messages     对话消息
 * @param tools        工具定义（仅 TOOLS）
 * @param jsonMode     要求 JSON 输出（response_format=json_object）
 * @param traceId      关联追踪 id（可空）
 * @param memberId     调用人（可空）
 * @param ruleFallback 规则兜底（可空）
 * @param forceRule    直接走规则（配额用尽）
 * @param thinking     思考模式覆盖（null = 用路由配置；如评测裁判固定 LOW）
 */
public record ChatRequest(Capability capability, String scene, List<ChatMessage> messages, List<ToolDef> tools,
                          boolean jsonMode, String traceId, Long memberId, RuleResponder ruleFallback,
                          boolean forceRule, ThinkingMode thinking) {

    public ChatRequest(Capability capability, String scene, List<ChatMessage> messages, List<ToolDef> tools,
                       boolean jsonMode, String traceId, Long memberId, RuleResponder ruleFallback, boolean forceRule) {
        this(capability, scene, messages, tools, jsonMode, traceId, memberId, ruleFallback, forceRule, null);
    }

    public ChatRequest {
        if (capability == null) throw new IllegalArgumentException("capability 不能为空");
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        scene = scene == null || scene.isBlank() ? capability.name().toLowerCase() : scene;
    }

    public static ChatRequest text(String scene, String system, String user, boolean jsonMode) {
        return new ChatRequest(Capability.CHAT, scene, List.of(ChatMessage.system(system), ChatMessage.user(user)),
                List.of(), jsonMode, null, null, null, false);
    }

    public ChatRequest withMessages(List<ChatMessage> newMessages) {
        return new ChatRequest(capability, scene, newMessages, tools, jsonMode, traceId, memberId, ruleFallback,
                forceRule, thinking);
    }

    /** 最后一条 user 消息文本（规则兜底用）。 */
    public String lastUserText() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) return messages.get(i).content();
        }
        return "";
    }
}
