package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.Lookup;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** get_recipe：按菜名或 id 取用料与步骤。 */
public class GetRecipeTool implements AgentTool {

    public static final String NAME = "get_recipe";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "查看菜谱";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "按菜名（或 id）查看一道菜的用料、步骤、用时。",
                schema(Map.of("dish", prop("string", "菜名或 id")), "dish"));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        String dish = str(args, "dish");
        if (dish == null) return ToolOutcome.fail("缺少菜名");
        Lookup<DishInfo> l = ctx.kitchen().findDish(dish);
        switch (l.status()) {
            case FORBIDDEN:
                return ToolOutcome.fail("无权访问或不存在：" + dish);
            case NOT_FOUND:
                return ToolOutcome.fail("没找到「" + dish + "」");
            case AMBIGUOUS:
                return ToolOutcome.fail("有多道菜叫这个名字，需要用户确认",
                        Map.of("needClarify", true, "candidates", l.candidates().stream().map(DishInfo::name).toList()));
            default:
                break;
        }
        DishInfo d = l.value();
        Map<String, Object> data = new LinkedHashMap<>(dishForModel(d));
        data.put("ingredients", d.ingredients().stream()
                .map(i -> i.name() + (i.amount() == null ? "" : " " + i.amount())).toList());
        data.put("steps", d.steps().stream().limit(10).toList());
        if (d.description() != null && !d.description().isBlank()) data.put("description", d.description());
        return ToolOutcome.ok(d.name() + "：" + d.ingredients().size() + " 样用料，" + d.steps().size() + " 步", data,
                List.of(recipesCard(List.of(d))));
    }
}
