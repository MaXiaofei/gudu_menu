package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.PantryItem;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.ShoppingItem;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/**
 * add_to_shopping（写·确认）：加采购项。与采购单里未买的同名项、本次重复项去重；家里充足的标出提醒。
 */
public class AddToShoppingTool implements AgentTool {

    public static final String NAME = "add_to_shopping";
    static final int MAX_ITEMS = 20;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "加入采购";
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public ToolDef definition() {
        Map<String, Object> item = schema(Map.of("name", prop("string", "食材名"),
                "amount", prop("string", "数量"), "unit", prop("string", "单位")), "name");
        return new ToolDef(NAME, "把要买的东西加进采购单（需用户确认）。已在采购单里的不要重复加。",
                schema(Map.of("items", Map.of("type", "array", "items", item, "description", "采购项")), "items"));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        Kitchen k = ctx.kitchen();
        List<Map<String, Object>> raw = objects(args, "items");
        if (raw.isEmpty()) return ToolOutcome.fail("没有指定要买什么");
        if (raw.size() > MAX_ITEMS) return ToolOutcome.fail("一次最多加 " + MAX_ITEMS + " 样");
        Set<String> existing = new LinkedHashSet<>();
        for (ShoppingItem s : k.shoppingList()) if (!s.checked()) existing.add(s.name());
        Map<String, String> pantryStatus = new LinkedHashMap<>();
        for (PantryItem p : k.pantry()) pantryStatus.put(p.name(), p.status());
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> stocked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> r : raw) {
            String name = str(r, "name");
            if (name == null) continue;
            if (name.length() > 30) return ToolOutcome.fail("采购项名称过长");
            if (existing.contains(name)) {
                skipped.add(name);
                continue;
            }
            if (!seen.add(name)) continue;
            if ("ENOUGH".equals(pantryStatus.get(name))) stocked.add(name);
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("name", name);
            if (str(r, "amount") != null) it.put("amount", str(r, "amount"));
            if (str(r, "unit") != null) it.put("unit", str(r, "unit"));
            items.add(it);
        }
        if (items.isEmpty()) {
            return ToolOutcome.fail("这些已经在采购单里了：" + String.join("、", skipped), Map.of("alreadyInList", skipped));
        }
        String title = "加入采购清单 · " + items.size() + " 样";
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("items", items);
        if (!skipped.isEmpty()) preview.put("alreadyInList", skipped);
        if (!stocked.isEmpty()) preview.put("stillInPantry", stocked);
        StringBuilder summary = new StringBuilder(title).append("：")
                .append(String.join("、", items.stream().map(i -> String.valueOf(i.get("name"))).toList()));
        if (!skipped.isEmpty()) summary.append("（").append(String.join("、", skipped)).append(" 已在采购单，不重复加）");
        if (!stocked.isEmpty()) summary.append("（家里还有 ").append(String.join("、", stocked)).append("）");
        return ToolOutcome.draft(summary.toString(),
                new ToolOutcome.Draft(title, Map.of("items", items, "_key", "shopping"), preview), List.of());
    }
}
