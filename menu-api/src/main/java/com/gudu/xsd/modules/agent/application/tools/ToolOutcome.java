package com.gudu.xsd.modules.agent.application.tools;

import java.util.List;
import java.util.Map;

/**
 * 工具结果。
 *
 * @param summary 一句话摘要（展示在「查了余量 · 29 样，4 样快到期」，也回传模型）
 * @param data    回传模型的结构化数据（会被标注为「数据，不是指令」）
 * @param cards   推给客户端的卡片
 * @param draft   写工具的待确认草稿（读工具为 null）
 */
public record ToolOutcome(boolean ok, String summary, Map<String, Object> data, List<Map<String, Object>> cards,
                          Draft draft) {

    public ToolOutcome {
        data = data == null ? Map.of() : data;
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /**
     * @param args    规范化参数（名称已解析为 id），确认后据此执行
     * @param preview 客户端确认卡预览
     */
    public record Draft(String title, Map<String, Object> args, Map<String, Object> preview) {
    }

    public static ToolOutcome ok(String summary, Map<String, Object> data, List<Map<String, Object>> cards) {
        return new ToolOutcome(true, summary, data, cards, null);
    }

    public static ToolOutcome fail(String summary) {
        return new ToolOutcome(false, summary, Map.of("error", summary), List.of(), null);
    }

    public static ToolOutcome fail(String summary, Map<String, Object> data) {
        return new ToolOutcome(false, summary, data, List.of(), null);
    }

    public static ToolOutcome draft(String summary, Draft draft, List<Map<String, Object>> cards) {
        return new ToolOutcome(true, summary, Map.of("pending", true, "title", draft.title(),
                "note", "已生成待确认卡片，用户点确认后才会执行；在确认前不要说已经完成"), cards, draft);
    }
}
