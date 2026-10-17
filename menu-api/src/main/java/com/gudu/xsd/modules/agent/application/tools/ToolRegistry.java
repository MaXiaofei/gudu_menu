package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 工具注册表：eval.md §7 的 15 个工具 + cancel_action。 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        for (AgentTool t : List.of(new SearchRecipesTool(), new GetRecipeTool(), new GetPantryTool(),
                new GetFamilyProfileTool(), new RecommendTonightTool(), new ListMenusTool(), new AddToMenuTool(),
                new RemoveFromMenuTool(), new GetShoppingListTool(), new AddToShoppingTool(), new MarkCookedTool(),
                new AddPantryTool(), new UndoActionTool(), new CancelActionTool(), new PlanWeekTool())) {
            tools.put(t.name(), t);
        }
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name));
    }

    public List<ToolDef> definitions() {
        return tools.values().stream().map(AgentTool::definition).toList();
    }

    public List<AgentTool> all() {
        return List.copyOf(tools.values());
    }

    /** 需要确认的写工具名。 */
    public Set<String> writeTools() {
        return tools.values().stream().filter(AgentTool::write).map(AgentTool::name).collect(Collectors.toSet());
    }
}
