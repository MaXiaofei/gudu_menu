package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.CookLine;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.CookPreview;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.ItemLine;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.Lookup;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MemberInfo;
import com.gudu.xsd.modules.agent.domain.service.ProfileConstraintPolicy;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/**
 * mark_cooked（写·确认）：做完回写。预览给出用料去向（用完 / 剩余 / 调料不扣），确认后写做饭记录并扣余量，可撤销。
 * 宝宝吃了辣 / 重口的菜在预览里提醒。
 */
public class MarkCookedTool implements AgentTool {

    public static final String NAME = "mark_cooked";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "做完回写";
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public ToolDef definition() {
        Map<String, Object> override = schema(Map.of("name", prop("string", "食材名"),
                "amount", prop("string", "实际用量"), "unit", prop("string", "单位")), "name", "amount");
        return new ToolDef(NAME, "记录做完一道菜并按用量扣减余量（需用户确认）。servings 为几人份，eaters 为谁吃了（成员名）。",
                schema(Map.of(
                        "dish", prop("string", "菜名"),
                        "servings", prop("integer", "几人份"),
                        "eaters", stringArray("谁吃了（成员名）"),
                        "date", prop("string", "哪天做的 yyyy-MM-dd，默认今天"),
                        "overrides", Map.of("type", "array", "items", override, "description", "实际用量覆盖默认扣减")),
                        "dish", "servings"));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        Kitchen k = ctx.kitchen();
        String ref = str(args, "dish");
        if (ref == null) return ToolOutcome.fail("没有说是哪道菜");
        Lookup<DishInfo> l = k.findDish(ref);
        ToolOutcome dp = NameResolver.dishProblem(NameResolver.dishes(k, List.of(ref)));
        if (dp != null) return dp;
        DishInfo dish = l.value();
        Integer servings = integer(args, "servings");
        if (servings == null) servings = k.members().size();
        if (servings < 1 || servings > 20) return ToolOutcome.fail("人份需在 1–20 之间");
        NameResolver.Members eaters = NameResolver.members(k, strings(args, "eaters"));
        if (!eaters.unknown().isEmpty()) {
            return ToolOutcome.fail("家里没有这些成员：" + String.join("、", eaters.unknown()));
        }
        LocalDate date;
        try {
            date = str(args, "date") == null ? k.now().toLocalDate() : LocalDate.parse(str(args, "date"));
        } catch (RuntimeException e) {
            return ToolOutcome.fail("日期格式应为 yyyy-MM-dd");
        }
        if (date.isAfter(k.now().toLocalDate())) return ToolOutcome.fail("不能记录未来的做饭");
        List<ItemLine> overrides = new ArrayList<>();
        for (Map<String, Object> o : objects(args, "overrides")) {
            if (str(o, "name") != null) overrides.add(new ItemLine(str(o, "name"), str(o, "amount"), str(o, "unit")));
        }
        CookPreview p = k.previewCook(dish.id(), servings, overrides);
        List<String> warnings = new ArrayList<>();
        for (MemberInfo m : eaters.found()) {
            if (ProfileConstraintPolicy.isBaby(m) && dish.tags().stream().anyMatch(t -> t.equals("辣") || t.equals("重口"))) {
                warnings.add(m.name() + " 是宝宝，这道菜偏辣偏重口");
            }
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("dishId", dish.id());
        normalized.put("dishName", dish.name());
        normalized.put("servings", servings);
        normalized.put("eaterIds", eaters.found().stream().map(MemberInfo::id).toList());
        normalized.put("date", date.toString());
        normalized.put("overrides", overrides.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", o.name());
            m.put("amount", o.amount());
            if (o.unit() != null) m.put("unit", o.unit());
            return (Object) m;
        }).toList());
        normalized.put("_key", "cooked:" + dish.id());
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("dish", dishCard(dish));
        preview.put("servings", servings);
        preview.put("date", date.toString());
        preview.put("eaters", eaters.found().stream().map(m -> Map.of("memberId", m.id(), "name", m.name(),
                "avatarUrl", m.avatarUrl() == null ? "" : m.avatarUrl())).toList());
        preview.put("lines", p.lines().stream().map(MarkCookedTool::line).toList());
        if (!warnings.isEmpty()) preview.put("warnings", warnings);
        String title = "做完了：" + dish.name() + " · " + servings + " 人份";
        String deltas = String.join("、", p.lines().stream().filter(x -> !"SKIP".equals(x.status()))
                .map(x -> x.name() + " " + x.delta() + "（" + x.after() + "）").toList());
        String summary = title + (deltas.isEmpty() ? "" : "，" + deltas) + (warnings.isEmpty() ? "" : "；注意：" + String.join("；", warnings));
        return ToolOutcome.draft(summary, new ToolOutcome.Draft(title, normalized, preview), List.of());
    }

    private static Map<String, Object> line(CookLine l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", l.name());
        m.put("iconKey", l.iconKey());
        m.put("delta", l.delta());
        m.put("after", l.after());
        m.put("status", l.status());
        return m;
    }
}
