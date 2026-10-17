package com.gudu.xsd.modules.agent.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 把历史 assistant 消息的卡片 / 动作压成一行附注，拼进历史文本：
 * 模型据此做指代消解（「就这三道」「改成四个人」），规则兜底也靠它找回上文的菜与卡片。
 */
public final class HistoryFormatter {

    private HistoryFormatter() {
    }

    /** 一张动作的历史描述：工具名 + 关键参数 + 状态。 */
    public record ActionNote(String tool, Map<String, Object> args, String status) {
    }

    public static String assistantText(String text, List<Map<String, Object>> cards, List<ActionNote> actions) {
        StringBuilder sb = new StringBuilder(text == null ? "" : text);
        List<String> dishes = new ArrayList<>();
        for (Map<String, Object> c : cards == null ? List.<Map<String, Object>>of() : cards) {
            Object items = c.get("items");
            if (items instanceof Collection<?> col) {
                for (Object it : col) {
                    String name = it instanceof Map<?, ?> m ? String.valueOf(m.get("name")) : String.valueOf(it);
                    if (!dishes.contains(name)) dishes.add(name);
                }
            }
        }
        if (!dishes.isEmpty()) sb.append("\n（展示过的菜：").append(String.join("、", dishes)).append("）");
        for (ActionNote a : actions == null ? List.<ActionNote>of() : actions) {
            sb.append("\n（").append("PENDING".equals(a.status()) ? "待确认卡片" : "动作[" + a.status() + "]").append("：")
                    .append(a.tool());
            Map<String, Object> raw = a.args();
            if (raw != null) {
                for (String k : List.of("dish", "dishName", "servings", "menu", "menuName", "dishes", "dishNames")) {
                    if (raw.get(k) != null) sb.append(' ').append(k.replace("Name", "")).append('=').append(flat(raw.get(k)));
                }
            }
            sb.append("）");
        }
        return sb.toString();
    }

    private static String flat(Object v) {
        if (v instanceof Collection<?> c) return String.join("、", c.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }
}
