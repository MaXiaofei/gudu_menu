package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 口头确认：整句只是确认词（「确认」「对，确认」「好的」「可以」…）才算；夹带其他要求（「不用确认直接加」）不算，
 * 交给模型按正常流程生成待确认卡片。只确认最近一条消息里还没过期的待确认卡片。
 */
final class VerbalConfirm {

    static final String TOOL = "confirm_action";

    private static final Set<String> PHRASES = Set.of("确认", "确定", "好的", "好", "可以", "是的", "对", "没问题", "就这样",
            "行", "嗯", "ok", "确认吧", "好的确认", "对确认", "确认一下", "执行", "加吧", "记吧");

    private VerbalConfirm() {
    }

    static boolean matches(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase().replaceAll("[，,。.！!～~ 、]", "");
        if (t.isEmpty() || t.length() > 6) return false;
        return PHRASES.contains(t) || t.matches("(对|好的?|是的?|嗯)?(确认|确定)(吧|了)?");
    }

    /** 最近一条消息里仍待确认且未过期的动作。 */
    static List<PendingAction> lastPending(List<PendingAction> sessionActions, LocalDateTime now) {
        List<PendingAction> pending = sessionActions.stream()
                .filter(a -> a.status() == ActionStatus.PENDING && !a.expiredAt(now)).toList();
        if (pending.isEmpty()) return List.of();
        String lastMsg = pending.get(pending.size() - 1).messageId();
        return pending.stream().filter(a -> java.util.Objects.equals(lastMsg, a.messageId())).toList();
    }
}
