package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.ShoppingItem;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** get_shopping_list：采购单（可按食集过滤）。 */
public class GetShoppingListTool implements AgentTool {

    public static final String NAME = "get_shopping_list";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "查看采购单";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "查看采购单里已有的东西（加采购前先看，避免重复）。",
                schema(Map.of("menu", prop("string", "只看某个食集的采购项"))));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        String menu = str(args, "menu");
        List<ShoppingItem> items = ctx.kitchen().shoppingList().stream()
                .filter(i -> menu == null || (i.forMenu() != null && (i.forMenu().contains(menu) || menu.contains(i.forMenu()))))
                .toList();
        List<Map<String, Object>> data = items.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", i.name());
            if (i.amount() != null) m.put("amount", i.amount());
            if (i.unit() != null) m.put("unit", i.unit());
            if (i.forMenu() != null) m.put("forMenu", i.forMenu());
            m.put("checked", i.checked());
            return m;
        }).toList();
        String summary = items.isEmpty() ? "采购单是空的"
                : items.size() + " 样：" + String.join("、", items.stream().map(ShoppingItem::name).toList());
        return ToolOutcome.ok(summary, Map.of("items", data), List.of());
    }
}
