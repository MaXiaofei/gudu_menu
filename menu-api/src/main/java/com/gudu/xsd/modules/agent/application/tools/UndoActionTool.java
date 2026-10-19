package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** undo_action：撤销本会话最近一次（或指定）已执行的写操作；撤销本身无需二次确认。 */
public class UndoActionTool implements AgentTool {

    public static final String NAME = "undo_action";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "撤销";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "撤销本会话里刚执行过的写操作（加食集 / 加采购 / 做完回写 / 入库），5 分钟内有效，无需再次确认。",
                schema(Map.of("action", prop("string", "动作 id 或描述；不填 = 最近一次"))));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        try {
            PendingAction undone = ctx.ledger().undo(str(args, "action"));
            return ToolOutcome.ok("已撤销：" + undone.title(), Map.of("undone", undone.actionId(),
                    "title", undone.title()), List.of());
        } catch (DomainException e) {
            return ToolOutcome.fail(e.getMessage());
        }
    }
}
