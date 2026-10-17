package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.MenuInfo;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** list_menus：食集（进行中 / 接下来 / 已完成，可按日期）。 */
public class ListMenusTool implements AgentTool {

    public static final String NAME = "list_menus";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "查看食集";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "列出家里的食集（一顿饭的计划），可按状态或日期过滤。",
                schema(Map.of("status", enumProp("状态", "ONGOING", "UPCOMING", "DONE"),
                        "date", prop("string", "日期 yyyy-MM-dd"))));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        String status = str(args, "status");
        String date = str(args, "date");
        LocalDate d = null;
        if (date != null) {
            try {
                d = LocalDate.parse(date);
            } catch (RuntimeException e) {
                return ToolOutcome.fail("日期格式应为 yyyy-MM-dd");
            }
        }
        LocalDate day = d;
        List<MenuInfo> menus = ctx.kitchen().menus().stream()
                .filter(m -> status == null || status.equalsIgnoreCase(m.status()))
                .filter(m -> day == null || day.equals(m.date()))
                .toList();
        List<Map<String, Object>> items = menus.stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", m.name());
            x.put("date", m.date() == null ? null : m.date().toString());
            x.put("servings", m.servings());
            x.put("status", m.status());
            x.put("dishes", m.dishNames());
            if (m.note() != null && !m.note().isBlank()) x.put("userNote", m.note());
            return x;
        }).toList();
        List<Map<String, Object>> cards = menus.stream().limit(3).map(ListMenusTool::card).toList();
        String summary = menus.isEmpty() ? "没有符合条件的食集"
                : menus.size() + " 个：" + String.join("、", menus.stream().map(MenuInfo::name).toList());
        return ToolOutcome.ok(summary, Map.of("menus", items), cards);
    }

    static Map<String, Object> card(MenuInfo m) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("type", "menu");
        c.put("menuId", m.id());
        c.put("name", m.name());
        c.put("date", m.date() == null ? null : m.date().toString());
        c.put("dishCount", m.dishNames().size());
        c.put("coverUrls", m.coverUrls());
        return c;
    }
}
