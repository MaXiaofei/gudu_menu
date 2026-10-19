package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.PantryItem;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** get_pantry：余量（按档位 / 临期天数 / 名称过滤）。 */
public class GetPantryTool implements AgentTool {

    public static final String NAME = "get_pantry";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "查了余量";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "查家里的食材余量：档位（充足/不足/用完）、保质期剩几天。可按档位、临期天数、名称过滤。",
                schema(Map.of(
                        "status", enumProp("只看某档位", "ENOUGH", "LOW", "OUT"),
                        "expiringDays", prop("integer", "只看 N 天内到期的"),
                        "names", stringArray("只看这些食材"))));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        List<PantryItem> all = ctx.kitchen().pantry();
        String status = str(args, "status");
        Integer days = integer(args, "expiringDays");
        List<String> names = strings(args, "names");
        List<PantryItem> hit = new ArrayList<>();
        for (PantryItem p : all) {
            if (status != null && !status.equalsIgnoreCase(p.status())) continue;
            if (days != null && (p.daysLeft() == null || p.daysLeft() > days)) continue;
            if (!names.isEmpty() && names.stream().noneMatch(n -> p.name().contains(n) || n.contains(p.name()))) continue;
            hit.add(p);
        }
        hit.sort(Comparator.comparing((PantryItem p) -> p.daysLeft() == null ? Integer.MAX_VALUE : p.daysLeft())
                .thenComparing(PantryItem::name));
        long expiring3 = all.stream().filter(p -> p.daysLeft() != null && p.daysLeft() <= 3 && !"OUT".equals(p.status()))
                .count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", all.size());
        data.put("expiringIn3Days", expiring3);
        data.put("items", hit.stream().limit(20).map(GetPantryTool::forModel).toList());
        List<String> missing = names.stream()
                .filter(n -> all.stream().noneMatch(p -> p.name().contains(n) || n.contains(p.name()))).toList();
        if (!missing.isEmpty()) data.put("notInPantry", missing);
        String summary;
        if (!names.isEmpty()) {
            summary = hit.isEmpty() ? "家里没有记录：" + String.join("、", names)
                    : String.join("，", hit.stream().map(GetPantryTool::brief).toList());
        } else if (days != null) {
            summary = hit.isEmpty() ? days + " 天内没有要到期的" : hit.size() + " 样 " + days + " 天内到期：" + String.join("、",
                    hit.stream().map(PantryItem::name).limit(6).toList());
        } else {
            summary = all.size() + " 样，" + expiring3 + " 样快到期";
        }
        Map<String, Object> card = Map.of("type", "pantry", "items", hit.stream().limit(8).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("iconKey", p.iconKey());
            m.put("status", p.status());
            m.put("daysLeft", p.daysLeft());
            return (Object) m;
        }).toList());
        return ToolOutcome.ok(summary, data, hit.isEmpty() ? List.of() : List.of(card));
    }

    private static Map<String, Object> forModel(PantryItem p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name());
        m.put("status", p.status());
        if (p.amount() != null) m.put("amount", p.amount());
        if (p.daysLeft() != null) m.put("daysLeft", p.daysLeft());
        if (p.note() != null && !p.note().isBlank()) m.put("userNote", p.note());
        return m;
    }

    private static String brief(PantryItem p) {
        String level = switch (p.status() == null ? "" : p.status()) {
            case "ENOUGH" -> "充足";
            case "LOW" -> "不足";
            case "OUT" -> "用完了";
            default -> p.status();
        };
        String exp = p.daysLeft() == null ? "" : p.daysLeft() <= 0 ? "，今天到期" : p.daysLeft() == 1 ? "，明天到期"
                : "，" + p.daysLeft() + " 天后到期";
        return p.name() + level + exp;
    }
}
