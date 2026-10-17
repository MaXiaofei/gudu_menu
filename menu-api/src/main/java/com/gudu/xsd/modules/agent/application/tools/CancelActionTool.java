package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** cancel_action：用户说「算了 / 不加了」时取消还没确认的卡片（不会写任何数据）。 */
public class CancelActionTool implements AgentTool {

    public static final String NAME = "cancel_action";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "取消待确认";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "用户反悔时取消还没确认的卡片。不填 action 则取消本会话全部待确认卡片。",
                schema(Map.of("action", prop("string", "动作 id 或描述"))));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        List<PendingAction> cancelled = ctx.ledger().cancel(str(args, "action"));
        if (cancelled.isEmpty()) return ToolOutcome.ok("没有待确认的卡片", Map.of("cancelled", List.of()), List.of());
        return ToolOutcome.ok("已取消：" + String.join("、", cancelled.stream().map(PendingAction::title).toList()),
                Map.of("cancelled", cancelled.stream().map(PendingAction::actionId).toList()), List.of());
    }
}
