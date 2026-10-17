package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** add_pantry（写·确认）：入库（数量、单位、保质期）。 */
public class AddPantryTool implements AgentTool {

    public static final String NAME = "add_pantry";
    static final int MAX_ITEMS = 20;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "放进余量";
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public ToolDef definition() {
        Map<String, Object> item = schema(Map.of("name", prop("string", "食材名"),
                "amount", prop("string", "数量（把「两斤」换算成数字 + 单位）"), "unit", prop("string", "单位"),
                "bestBefore", prop("string", "保质期 yyyy-MM-dd，可空")), "name");
        return new ToolDef(NAME, "把买回来的东西放进余量（需用户确认）。",
                schema(Map.of("items", Map.of("type", "array", "items", item, "description", "入库项")), "items"));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        List<Map<String, Object>> raw = objects(args, "items");
        if (raw.isEmpty()) return ToolOutcome.fail("没有指定要放进余量的东西");
        if (raw.size() > MAX_ITEMS) return ToolOutcome.fail("一次最多 " + MAX_ITEMS + " 样");
        LocalDate today = ctx.kitchen().now().toLocalDate();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            String name = str(r, "name");
            if (name == null) continue;
            if (name.length() > 30) return ToolOutcome.fail("食材名称过长");
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("name", name);
            if (str(r, "amount") != null) it.put("amount", str(r, "amount"));
            if (str(r, "unit") != null) it.put("unit", str(r, "unit"));
            String bb = str(r, "bestBefore");
            if (bb != null) {
                try {
                    LocalDate d = LocalDate.parse(bb);
                    if (d.isBefore(today)) return ToolOutcome.fail(name + " 的保质期早于今天");
                    it.put("bestBefore", d.toString());
                } catch (RuntimeException e) {
                    return ToolOutcome.fail("保质期格式应为 yyyy-MM-dd");
                }
            }
            items.add(it);
        }
        if (items.isEmpty()) return ToolOutcome.fail("没有有效的入库项");
        String title = "放进余量 · " + items.size() + " 样";
        String names = String.join("、", items.stream().map(i -> i.get("name")
                + (i.get("amount") == null ? "" : " " + i.get("amount") + (i.get("unit") == null ? "" : i.get("unit")))).toList());
        return ToolOutcome.draft(title + "：" + names,
                new ToolOutcome.Draft(title, Map.of("items", items, "_key", "pantry"), Map.of("items", items)), List.of());
    }
}
