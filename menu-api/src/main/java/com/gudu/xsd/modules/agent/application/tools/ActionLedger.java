package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.model.PendingAction;

import java.util.List;

/** 本会话的写操作台账：供 undo_action / cancel_action 工具使用。 */
public interface ActionLedger {

    /** 本会话全部动作（创建时间正序）。 */
    List<PendingAction> sessionActions();

    /**
     * 撤销已执行的动作（ref 为 actionId 或标题片段；空 = 最近一次已执行的）。
     *
     * @return 被撤销的动作
     * @throws com.gudu.xsd.common.domain.DomainException 没有可撤销的 / 已超时
     */
    PendingAction undo(String ref);

    /** 取消待确认的动作（ref 为空 = 全部待确认的），返回被取消的。 */
    List<PendingAction> cancel(String ref);
}
