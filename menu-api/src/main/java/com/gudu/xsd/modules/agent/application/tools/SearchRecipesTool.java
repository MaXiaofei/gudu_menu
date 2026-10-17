package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishQuery;
import com.gudu.xsd.modules.agent.domain.service.IngredientSynonyms;
import com.gudu.xsd.modules.agent.domain.service.ProfileConstraintPolicy;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/**
 * search_recipes：按关键词 / 食材 / 标签 / 时长找菜。服务端强制合并被点名成员的画像约束（模型漏传也生效），
 * 结果里标出实际生效的约束，便于调用链定位「约束没进参数」类问题。
 */
public class SearchRecipesTool implements AgentTool {

    public static final String NAME = "search_recipes";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "检索菜谱";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        Map<String, Object> filters = schema(Map.of(
                "tags", stringArray("必须包含的标签，如 少盐、宝宝可吃、快手"),
                "excludeTags", stringArray("排除的标签，如 辣、重口、油炸"),
                "maxMinutes", prop("integer", "最长用时（分钟）"),
                "forMembers", stringArray("给谁吃（成员名），服务端按其画像追加约束")));
        return new ToolDef(NAME, "按关键词、食材、标签、用时检索家里能做的菜，返回菜名、用时、标签、家里够几样。"
                + "涉及宝宝/老人/有健康档案的成员时，务必在 filters.forMembers 或 excludeTags 里带上约束。",
                schema(Map.of(
                        "query", prop("string", "关键词或描述"),
                        "ingredients", stringArray("想用掉的食材名"),
                        "filters", filters,
                        "limit", prop("integer", "返回条数，默认 3，最多 8"))));
    }

    /** 约束键放错层级（与 query 同级）时挪回 filters；检索词做同义归一（西蓝花→西兰花、洋芋→土豆）。 */
    @Override
    public Map<String, Object> normalize(Map<String, Object> args) {
        Map<String, Object> out = new LinkedHashMap<>(args);
        Map<String, Object> filters = new LinkedHashMap<>(map(args, "filters"));
        for (String k : List.of("tags", "excludeTags", "forMembers", "maxMinutes")) {
            Object top = out.remove(k);
            if (top == null) continue;
            Object inner = filters.get(k);
            if (inner == null) {
                filters.put(k, top);
            } else if (inner instanceof List<?> a && top instanceof List<?> b) {
                Set<Object> merged = new LinkedHashSet<>(a);
                merged.addAll(b);
                filters.put(k, List.copyOf(merged));
            }
        }
        if (!filters.isEmpty()) out.put("filters", filters);
        if (out.get("query") instanceof String q) out.put("query", IngredientSynonyms.canonical(q));
        if (out.get("ingredients") instanceof List<?> ings) {
            out.put("ingredients", ings.stream().map(x -> IngredientSynonyms.canonical(String.valueOf(x))).distinct().toList());
        }
        return out;
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        Map<String, Object> filters = map(args, "filters");
        ProfileConstraintPolicy.Constraint forced = forcedConstraint(ctx, strings(filters, "forMembers"));
        Set<String> exclude = new LinkedHashSet<>(strings(filters, "excludeTags"));
        exclude.addAll(forced.excludeTags());
        Integer limit = integer(args, "limit");
        DishQuery q = new DishQuery(str(args, "query"), strings(args, "ingredients"), strings(filters, "tags"),
                exclude, forced.preferTags(), integer(filters, "maxMinutes"), limit == null ? 3 : limit);
        List<DishInfo> dishes = ctx.kitchen().searchDishes(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", dishes.stream().map(ToolSupport::dishForModel).toList());
        data.put("constraintsApplied", Map.of("excludeTags", List.copyOf(exclude), "notes", forced.notes()));
        if (dishes.isEmpty()) {
            return ToolOutcome.ok("没找到符合条件的菜", data, List.of());
        }
        return ToolOutcome.ok("找到 " + dishes.size() + " 道：" + joinNames(dishes), data,
                List.of(recipesCard(dishes)));
    }
}
