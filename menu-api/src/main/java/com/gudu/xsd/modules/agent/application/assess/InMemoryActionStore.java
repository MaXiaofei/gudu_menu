package com.gudu.xsd.modules.agent.application.assess;

import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.UndoTicket;
import com.gudu.xsd.modules.agent.domain.repository.PendingActionStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 评测用内存动作存储（与 Redis 实现同语义；单线程使用）。 */
public class InMemoryActionStore implements PendingActionStore {

    private final Map<String, PendingAction> actions = new LinkedHashMap<>();
    private final Map<String, UndoTicket> undo = new LinkedHashMap<>();

    @Override
    public void save(PendingAction action) {
        actions.put(action.actionId(), action);
    }

    @Override
    public Optional<PendingAction> find(String actionId) {
        return Optional.ofNullable(actions.get(actionId));
    }

    @Override
    public boolean transition(String actionId, ActionStatus from, ActionStatus to) {
        PendingAction a = actions.get(actionId);
        if (a == null || a.status() != from) return false;
        actions.put(actionId, a.withStatus(to));
        return true;
    }

    @Override
    public List<PendingAction> listBySession(String sessionId) {
        List<PendingAction> out = new ArrayList<>();
        for (PendingAction a : actions.values()) if (sessionId.equals(a.sessionId())) out.add(a);
        return out;
    }

    @Override
    public void saveUndo(UndoTicket ticket) {
        undo.put(ticket.token(), ticket);
    }

    @Override
    public Optional<UndoTicket> takeUndo(String token) {
        return Optional.ofNullable(undo.remove(token));
    }

    /** 评测预置：脚本里已执行（DONE）的动作要能被撤销，用脚本给的 undoToken 登记。 */
    public void putUndo(UndoTicket ticket) {
        undo.put(ticket.token(), ticket);
    }

    public List<PendingAction> all() {
        return List.copyOf(actions.values());
    }
}
