package com.gudu.xsd.modules.agent.domain.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 「说了却没做」观测（只观测、不拦截）：回复声称已生成待确认卡片 / 已加好 / 已撤销，但本轮没有产生任何待确认动作，
 * 也没有成功的撤销 / 取消 / 口头确认。评测 1.3.0 复核发现 agent-v2 下这类回复占写操作失败的一部分（模型把
 * 「我准备生成卡片」说成「已生成」），先在调用链里记 guard span 量化，再决定是否自动补一轮让模型真正调用工具。
 */
public final class ActionClaimGuard {

    private static final Pattern CLAIM = Pattern.compile(
            "(卡片已经?生成|已经?生成.{0,6}卡片|生成好了|建好了.{0,6}卡片|给你建好|卡片出来了|点确认(就|才|后)|已经?(加好|加进去|记好|撤销|撤回|取消)了?)");

    private ActionClaimGuard() {
    }

    /**
     * @param reply         最终回复
     * @param actionsInTurn 本轮新产生的待确认动作数
     * @param doneToolNames 本轮成功执行的工具名（含 undo_action / cancel_action / confirm_action）
     * @return 命中的声称片段；没有矛盾时为 null
     */
    public static String claimWithoutAction(String reply, int actionsInTurn, List<String> doneToolNames) {
        if (reply == null || actionsInTurn > 0) return null;
        if (doneToolNames.stream().anyMatch(n -> n.equals("undo_action") || n.equals("cancel_action")
                || n.equals("confirm_action"))) {
            return null;
        }
        var m = CLAIM.matcher(reply);
        return m.find() ? m.group() : null;
    }
}
