package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.service.ProfileConstraintPolicy;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** recommend_tonight：今晚推荐（临期优先 + 常做），合并画像约束。 */
public class RecommendTonightTool implements AgentTool {

    public static final String NAME = "recommend_tonight";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "今晚推荐";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "按家里余量（临期优先）和常做的菜推荐今晚做什么。",
                schema(Map.of("n", prop("integer", "推荐几道，默认 3"),
                        "forMembers", stringArray("给谁吃（成员名）"))));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        ProfileConstraintPolicy.Constraint forced = forcedConstraint(ctx, strings(args, "forMembers"));
        Integer n = integer(args, "n");
        List<DishInfo> dishes = ctx.kitchen().recommendTonight(n == null ? 3 : Math.max(1, Math.min(n, 6)),
                forced.excludeTags());
        if (dishes.isEmpty()) return ToolOutcome.ok("暂时没有合适的推荐", Map.of("items", List.of()), List.of());
        return ToolOutcome.ok("推荐 " + joinNames(dishes),
                Map.of("items", dishes.stream().map(ToolSupport::dishForModel).toList(),
                        "constraintsApplied", forced.notes()),
                List.of(recipesCard(dishes)));
    }
}
