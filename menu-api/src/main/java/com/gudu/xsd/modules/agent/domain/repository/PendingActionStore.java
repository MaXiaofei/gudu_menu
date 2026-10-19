package com.gudu.xsd.modules.agent.domain.repository;

import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.UndoTicket;

import java.util.List;
import java.util.Optional;

/** 待确认动作与撤销凭据存储（线上 Redis，带 TTL；评测用内存实现）。 */
public interface PendingActionStore {

    /** 保存（覆盖同 id）。 */
    void save(PendingAction action);

    Optional<PendingAction> find(String actionId);

    /** 原子地把 from 状态改为 to（防重复确认）；当前不是 from 返回 false。 */
    boolean transition(String actionId, ActionStatus from, ActionStatus to);

    /** 本会话的动作（创建时间正序）。 */
    List<PendingAction> listBySession(String sessionId);

    void saveUndo(UndoTicket ticket);

    /** 取出并删除（只能用一次）。 */
    Optional<UndoTicket> takeUndo(String token);
}
