package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.Map;

/**
 * 咕嘟工具：领域能力的薄适配层。读工具直接执行；写工具只产出 {@link ToolOutcome#draft()}（待确认），
 * 真正落库在用户确认后由 {@link WriteExecutor} 执行。签名见 eval.md §7。
 */
public interface AgentTool {

    String name();

    /** 展示名（「查了余量」）。 */
    String label();

    boolean write();

    ToolDef definition();

    ToolOutcome run(ToolContext ctx, Map<String, Object> args);
}
