package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.agent.application.tools.ActionLedger;
import com.gudu.xsd.modules.agent.application.tools.ToolOutcome;
import com.gudu.xsd.modules.agent.application.tools.WriteExecutor;
import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.UndoTicket;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.WriteResult;
import com.gudu.xsd.modules.agent.domain.repository.PendingActionStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 写操作的确认台：登记草稿（同目标的旧卡片被新卡片替代）、确认执行、取消、撤销。
 * 线上与评测共用（评测传入夹具厨房 + 内存存储）。时间统一取 {@link Kitchen#now()}。
 */
public class ActionDesk {

    private final PendingActionStore store;
    private final Kitchen kitchen;

    public ActionDesk(PendingActionStore store, Kitchen kitchen) {
        this.store = store;
        this.kitchen = kitchen;
    }

    public record Confirmed(PendingAction action, Map<String, Object> result, String undoToken) {
    }

    /** 登记待确认动作；同会话同目标（_key）还没确认的旧卡片标为取消（被替代），返回被替代的 id。 */
    public Registered register(Long memberId, String sessionId, String messageId, String tool, ToolOutcome.Draft draft) {
        Object key = draft.args().get("_key");
        List<String> superseded = new ArrayList<>();
        if (key != null && sessionId != null) {
            for (PendingAction old : store.listBySession(sessionId)) {
                if (old.status() == ActionStatus.PENDING && tool.equals(old.tool())
                        && Objects.equals(key, old.args().get("_key"))
                        && store.transition(old.actionId(), ActionStatus.PENDING, ActionStatus.SUPERSEDED)) {
                    superseded.add(old.actionId());
                }
            }
        }
        PendingAction a = PendingAction.draft("act_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                sessionId, messageId, memberId, tool, draft.title(), draft.args(), draft.preview(), kitchen.now());
        store.save(a);
        return new Registered(a, superseded);
    }

    public record Registered(PendingAction action, List<String> superseded) {
    }

    /** 用户点确认：校验本人 / 未过期 / 仍待确认 → 原子占位 → 执行 → 发撤销凭据。 */
    public Confirmed confirm(Long memberId, String actionId) {
        PendingAction a = store.find(actionId).orElseThrow(() -> new DomainException("动作不存在或已过期"));
        LocalDateTime now = kitchen.now();
        if (a.status() == ActionStatus.PENDING && a.expiredAt(now)) {
            store.transition(actionId, ActionStatus.PENDING, ActionStatus.EXPIRED);
        }
        a.assertConfirmable(memberId, now);
        if (!store.transition(actionId, ActionStatus.PENDING, ActionStatus.DONE)) {
            throw new DomainException("这条动作已处理过");
        }
        WriteResult r;
        try {
            r = WriteExecutor.execute(kitchen, a);
        } catch (RuntimeException e) {
            store.transition(actionId, ActionStatus.DONE, ActionStatus.PENDING);
            throw e;
        }
        String token = null;
        if (r.undoKind() != null) {
            token = "undo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            store.saveUndo(new UndoTicket(token, memberId, a.tool(), r.undoKind(), withAction(r.undoPlan(), actionId),
                    now.plus(UndoTicket.TTL)));
        }
        PendingAction done = a.done(token);
        store.save(done);
        return new Confirmed(done, r.result(), token);
    }

    public PendingAction cancel(Long memberId, String actionId) {
        PendingAction a = store.find(actionId).orElseThrow(() -> new DomainException("动作不存在或已过期"));
        if (!a.memberId().equals(memberId)) throw new DomainException("无权操作这条待确认动作");
        if (!store.transition(actionId, ActionStatus.PENDING, ActionStatus.CANCELLED)) {
            throw new DomainException("这条动作已处理过");
        }
        return a.withStatus(ActionStatus.CANCELLED);
    }

    /** 按撤销凭据撤销（结果卡上的「撤销」按钮）。 */
    public PendingAction undo(Long memberId, String token) {
        UndoTicket t = store.takeUndo(token).orElseThrow(() -> new DomainException("已超过可撤销时间"));
        t.assertUsable(memberId, kitchen.now());
        kitchen.undo(t.kind(), t.plan());
        Object actionId = t.plan().get("_actionId");
        if (actionId != null) {
            store.transition(String.valueOf(actionId), ActionStatus.DONE, ActionStatus.UNDONE);
            return store.find(String.valueOf(actionId)).orElse(null);
        }
        return null;
    }

    /** 绑定到会话的台账（undo_action / cancel_action 工具用）。 */
    public ActionLedger ledger(Long memberId, String sessionId) {
        return new ActionLedger() {
            @Override
            public List<PendingAction> sessionActions() {
                return sessionId == null ? List.of() : store.listBySession(sessionId);
            }

            @Override
            public PendingAction undo(String ref) {
                List<PendingAction> done = pick(sessionActions().stream()
                        .filter(a -> a.status() == ActionStatus.DONE && a.undoToken() != null).toList(), ref);
                if (done.isEmpty()) throw new DomainException("没有可以撤销的操作");
                PendingAction target = done.get(done.size() - 1);
                PendingAction r = ActionDesk.this.undo(memberId, target.undoToken());
                return r == null ? target.withStatus(ActionStatus.UNDONE) : r;
            }

            @Override
            public List<PendingAction> cancel(String ref) {
                List<PendingAction> out = new ArrayList<>();
                List<PendingAction> pending = pick(sessionActions().stream()
                        .filter(a -> a.status() == ActionStatus.PENDING).toList(), ref);
                for (PendingAction a : pending) {
                    if (store.transition(a.actionId(), ActionStatus.PENDING, ActionStatus.CANCELLED)) {
                        out.add(a.withStatus(ActionStatus.CANCELLED));
                    }
                }
                return out;
            }
        };
    }

    private static boolean matches(PendingAction a, String ref) {
        return ref == null || ref.isBlank() || a.actionId().equals(ref.trim()) || a.title().contains(ref.trim())
                || a.tool().equals(ref.trim());
    }

    /**
     * 按引用挑动作：先精确（空 = 全部、id、标题包含、工具名）；模型常把描述写成「mark_cooked 麻婆豆腐」
     * 「把凉拌菠菜加到今晚的饭」这类，精确不中时按词命中（工具名或标题/参数里出现的词）取得分最高者；
     * 仍不中但只有一个候选时就是它（用户说「撤销」时会话里只有这一件可撤）。
     */
    static List<PendingAction> pick(List<PendingAction> candidates, String ref) {
        List<PendingAction> exact = candidates.stream().filter(a -> matches(a, ref)).toList();
        if (!exact.isEmpty()) return exact;
        int best = 0;
        List<PendingAction> top = new ArrayList<>();
        for (PendingAction a : candidates) {
            int score = ref.contains(a.tool()) ? 1 : 0;
            for (String k : keywords(a)) {
                if (k.length() >= 2 && ref.contains(k)) score++;
            }
            if (score > best) {
                best = score;
                top = new ArrayList<>(List.of(a));
            } else if (score == best && score > 0) {
                top.add(a);
            }
        }
        if (!top.isEmpty()) return top;
        return candidates.size() == 1 ? candidates : List.of();
    }

    /** 动作里能被用户 / 模型拿来指代它的词：菜名、食集名、采购 / 入库项名、标题里「」中的名字。 */
    private static List<String> keywords(PendingAction a) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("「([^」]+)」").matcher(a.title());
        while (m.find()) out.add(m.group(1));
        collect(a.args(), out);
        return out;
    }

    private static void collect(Object v, List<String> out) {
        if (v instanceof String s) {
            out.add(s);
        } else if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                if (k.startsWith("_") && !"_raw".equals(k)) continue;
                if (List.of("dish", "dishes", "menu", "name", "items", "newMenu", "_raw").contains(k)) collect(e.getValue(), out);
            }
        } else if (v instanceof java.util.Collection<?> c) {
            for (Object x : c) collect(x, out);
        }
    }

    private static Map<String, Object> withAction(Map<String, Object> plan, String actionId) {
        Map<String, Object> m = new LinkedHashMap<>(plan);
        m.put("_actionId", actionId);
        return m;
    }
}
