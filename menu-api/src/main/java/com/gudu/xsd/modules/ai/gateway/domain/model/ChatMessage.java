package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.util.List;

/**
 * 对话消息（OpenAI 兼容格式的领域表示）。
 *
 * @param role             system / user / assistant / tool
 * @param content          文本
 * @param imageUrl         仅 user 消息：图片 URL 或 data URL（视觉能力）
 * @param toolCalls        仅 assistant：模型请求的工具调用
 * @param toolCallId       仅 tool：对应的调用 id
 * @param reasoningContent 仅 assistant：思考内容（思考模式 + 工具调用时须原样回传）
 */
public record ChatMessage(String role, String content, String imageUrl, List<ToolCall> toolCalls,
                          String toolCallId, String reasoningContent) {

    public ChatMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMessage system(String text) {
        return new ChatMessage("system", text, null, null, null, null);
    }

    public static ChatMessage user(String text) {
        return new ChatMessage("user", text, null, null, null, null);
    }

    public static ChatMessage userWithImage(String text, String imageUrl) {
        return new ChatMessage("user", text, imageUrl, null, null, null);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage("assistant", text, null, null, null, null);
    }

    public static ChatMessage assistantToolCalls(String text, List<ToolCall> calls, String reasoning) {
        return new ChatMessage("assistant", text, null, calls, null, reasoning);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, null, toolCallId, null);
    }

    /** 换图片（视觉 URL 下载失败时退回 base64 data URL）。 */
    public ChatMessage withImage(String newImageUrl) {
        return new ChatMessage(role, content, newImageUrl, toolCalls, toolCallId, reasoningContent);
    }
}
