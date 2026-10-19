package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.Lookup;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MenuInfo;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/**
 * add_to_menu（写·确认）：把菜加进已有食集或新建食集。没说哪个且有多个候选时不生成草稿，返回 needClarify。
 * 已在食集里的菜不重复添加。
 */
public class AddToMenuTool implements AgentTool {

    public static final String NAME = "add_to_menu";
    static final int MAX_DISHES = 10;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "加入食集";
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "把菜加入食集（需用户确认）。menu 用食集名；要新建时用 newMenu。没说加到哪个且有多个候选时先问用户。",
                schema(Map.of(
                        "menu", prop("string", "已有食集名或 id"),
                        "newMenu", schema(Map.of("name", prop("string", "食集名"),
                                "date", prop("string", "日期 yyyy-MM-dd（一周从周一开始）"),
                                "servings", prop("integer", "人数"))),
                        "dishes", stringArray("菜名")), "dishes"));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        Kitchen k = ctx.kitchen();
        List<String> names = strings(args, "dishes");
        if (names.isEmpty()) return ToolOutcome.fail("没有指定要加的菜");
        if (names.size() > MAX_DISHES) return ToolOutcome.fail("一次最多加 " + MAX_DISHES + " 道菜");
        NameResolver.Dishes dishes = NameResolver.dishes(k, names);
        ToolOutcome problem = NameResolver.dishProblem(dishes);
        if (problem != null) return problem;

        String menuRef = str(args, "menu");
        Map<String, Object> newMenu = map(args, "newMenu");
        MenuInfo menu = null;
        String newName = null;
        LocalDate newDate = null;
        int servings = 0;
        if (menuRef != null) {
            Lookup<MenuInfo> l = k.findMenu(menuRef);
            ToolOutcome mp = NameResolver.menuProblem(l, menuRef);
            if (mp != null) return mp;
            menu = l.value();
            if ("DONE".equals(menu.status())) return ToolOutcome.fail("「" + menu.name() + "」已经完成，不能再加菜");
        } else if (!newMenu.isEmpty()) {
            newName = str(newMenu, "name");
            String date = str(newMenu, "date");
            try {
                newDate = date == null ? k.now().toLocalDate() : LocalDate.parse(date);
            } catch (RuntimeException e) {
                return ToolOutcome.fail("日期格式应为 yyyy-MM-dd");
            }
            Integer s = integer(newMenu, "servings");
            servings = s == null ? k.members().size() : Math.max(1, Math.min(s, 30));
            if (newName == null) newName = newDate.getMonthValue() + "." + newDate.getDayOfMonth() + " 的饭";
        } else {
            List<MenuInfo> open = k.menus().stream().filter(m -> !"DONE".equals(m.status())).toList();
            if (open.size() == 1) {
                menu = open.get(0);
            } else if (open.size() > 1) {
                return ToolOutcome.fail("要加进哪个食集？", Map.of("needClarify", true,
                        "candidates", open.stream().map(MenuInfo::name).toList()));
            } else {
                newName = "今晚的饭";
                newDate = k.now().toLocalDate();
                servings = k.members().size();
            }
        }

        List<DishInfo> toAdd = new ArrayList<>();
        List<String> already = new ArrayList<>();
        for (DishInfo d : dishes.found()) {
            if (menu != null && menu.dishNames().contains(d.name())) already.add(d.name());
            else toAdd.add(d);
        }
        if (toAdd.isEmpty()) {
            return ToolOutcome.fail("这些菜已经在「" + menu.name() + "」里了", Map.of("alreadyInMenu", already));
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("dishIds", toAdd.stream().map(DishInfo::id).toList());
        normalized.put("dishNames", toAdd.stream().map(DishInfo::name).toList());
        Map<String, Object> menuPreview = new LinkedHashMap<>();
        if (menu != null) {
            normalized.put("menuId", menu.id());
            normalized.put("menuName", menu.name());
            normalized.put("_key", "menu:" + menu.id());
            menuPreview.put("menuId", menu.id());
            menuPreview.put("name", menu.name());
            menuPreview.put("date", menu.date() == null ? null : menu.date().toString());
            menuPreview.put("isNew", false);
        } else {
            normalized.put("newMenu", Map.of("name", newName, "date", newDate.toString(), "servings", servings));
            normalized.put("_key", "newMenu:" + newName);
            menuPreview.put("name", newName);
            menuPreview.put("date", newDate.toString());
            menuPreview.put("servings", servings);
            menuPreview.put("isNew", true);
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("menu", menuPreview);
        preview.put("dishes", toAdd.stream().map(ToolSupport::dishCard).toList());
        if (!already.isEmpty()) preview.put("alreadyInMenu", already);
        String target = menu != null ? menu.name() : newName;
        String title = "加入「" + target + "」· " + toAdd.size() + " 道菜";
        String summary = title + (already.isEmpty() ? "" : "（" + String.join("、", already) + " 已在里面，不重复加）");
        return ToolOutcome.draft(summary, new ToolOutcome.Draft(title, normalized, preview), List.of());
    }
}
