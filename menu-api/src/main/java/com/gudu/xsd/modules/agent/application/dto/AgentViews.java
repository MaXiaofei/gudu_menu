package com.gudu.xsd.modules.agent.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 咕嘟接口出入参（contracts.md §1）。 */
public final class AgentViews {

    private AgentViews() {
    }

    public record PageView<T>(List<T> items, String nextCursor) {
    }

    public record CreateSessionCommand(String title) {
    }

    public record SessionCreated(String sessionId) {
    }

    public record SessionView(String sessionId, String title, String lastMessage, LocalDateTime updatedAt,
                              String coverUrl) {
    }

    /**
     * @param audioUrl /file/upload 返回的语音地址（与 audioFileId 二选一；当前上传接口不返回数字 id）
     */
    public record SendCommand(String text, Long audioFileId, String audioUrl, Map<String, Object> context) {
    }

    public record ToolView(String name, String label, String summary, long ms, boolean ok) {
    }

    public record ActionView(String actionId, String tool, String title, Map<String, Object> args,
                             Map<String, Object> preview, LocalDateTime expiresAt, String status, String undoToken) {
    }

    public record MessageView(String messageId, String role, String text, List<ToolView> tools,
                              List<Map<String, Object>> cards, List<ActionView> actions, String traceId,
                              Integer rating, LocalDateTime createdAt) {
    }

    public record ConfirmView(String status, Map<String, Object> result, String undoToken) {
    }

    public record StatusView(String status) {
    }

    public record UndoCommand(String undoToken) {
    }

    public record ReviseCommand(Map<String, Object> args) {
    }

    public record FeedbackCommand(Integer rating, String comment) {
    }

    public record ExpiringView(String name, String iconKey, Integer daysLeft) {
    }

    public record SuggestionsView(String greeting, Map<String, Object> tonight, List<ExpiringView> expiring,
                                  List<String> prompts) {
    }

    // ------------------------------------------------------------------ 后台

    public record UserLite(Long userId, String nickname, String avatarUrl) {
    }

    public record TraceRow(String traceId, UserLite user, String firstText, long ms, String status,
                           LocalDateTime createdAt) {
    }

    public record TraceHeader(String traceId, String sessionId, UserLite user, String firstText, String status,
                              long ms, Long firstTokenMs, int tokens, BigDecimal costFen, String promptVersion,
                              String provider, String model, int phoneMasked, Integer feedback, boolean degraded,
                              LocalDateTime createdAt) {
    }

    public record SpanView(String spanId, String parentId, String type, String name, long startMs, long ms, int tokens,
                           BigDecimal costFen, boolean ok, Map<String, Object> input, Map<String, Object> output) {
    }

    public record TraceDetail(TraceHeader trace, List<SpanView> spans) {
    }

    public record ToolStat(String name, long calls, double successRate, long p95Ms) {
    }

    public record AgentStats(String date, long turns, double toolSuccessRate, long p95FirstTokenMs, BigDecimal costFen,
                             long guardBlocks, List<ToolStat> byTool) {
    }
}
