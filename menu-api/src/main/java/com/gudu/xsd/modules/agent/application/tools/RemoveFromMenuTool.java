package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.Lookup;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MenuInfo;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** remove_from_menu（写·确认）：从食集里拿掉菜。 */
public class RemoveFromMenuTool implements AgentTool {

    public static final String NAME = "remove_from_menu";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "移出食集";
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "把菜从食集里移除（需用户确认）。",
                schema(Map.of("menu", prop("string", "食集名或 id"), "dishes", stringArray("菜名")), "menu", "dishes"));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        String ref = str(args, "menu");
        if (ref == null) return ToolOutcome.fail("没有指定食集");
        Lookup<MenuInfo> l = ctx.kitchen().findMenu(ref);
        ToolOutcome mp = NameResolver.menuProblem(l, ref);
        if (mp != null) return mp;
        MenuInfo menu = l.value();
        NameResolver.Dishes dishes = NameResolver.dishes(ctx.kitchen(), strings(args, "dishes"));
        ToolOutcome problem = NameResolver.dishProblem(dishes);
        if (problem != null) return problem;
        List<DishInfo> inMenu = dishes.found().stream().filter(d -> menu.dishNames().contains(d.name())).toList();
        if (inMenu.isEmpty()) return ToolOutcome.fail("「" + menu.name() + "」里没有这些菜");
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("menuId", menu.id());
        normalized.put("menuName", menu.name());
        normalized.put("dishIds", inMenu.stream().map(DishInfo::id).toList());
        normalized.put("dishNames", inMenu.stream().map(DishInfo::name).toList());
        normalized.put("_key", "remove:" + menu.id());
        String title = "从「" + menu.name() + "」移除 " + joinNames(inMenu);
        return ToolOutcome.draft(title, new ToolOutcome.Draft(title, normalized,
                Map.of("menu", Map.of("menuId", menu.id(), "name", menu.name()),
                        "dishes", inMenu.stream().map(ToolSupport::dishCard).toList())), List.of());
    }
}
