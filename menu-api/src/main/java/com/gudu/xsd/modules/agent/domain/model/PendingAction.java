package com.gudu.xsd.modules.agent.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 待确认的写操作：写工具只生成它，不落库；用户确认后才执行。有效期 {@link #TTL}。
 *
 * @param args    工具参数（已解析为 id 的规范化参数，执行时直接用）
 * @param preview   展示给用户的预览（菜、用料去向、采购项…）
 * @param undoToken 执行后发放的撤销凭据（未执行为 null）
 */
public record PendingAction(String actionId, String sessionId, String messageId, Long memberId, String tool,
                            String title, Map<String, Object> args, Map<String, Object> preview,
                            LocalDateTime createdAt, LocalDateTime expiresAt, ActionStatus status,
                            String undoToken) {

    public static final Duration TTL = Duration.ofMinutes(5);

    public PendingAction {
        args = args == null ? Map.of() : Map.copyOf(args);
        preview = preview == null ? Map.of() : Map.copyOf(preview);
        status = status == null ? ActionStatus.PENDING : status;
    }

    public static PendingAction draft(String actionId, String sessionId, String messageId, Long memberId,
                                      String tool, String title, Map<String, Object> args,
                                      Map<String, Object> preview, LocalDateTime now) {
        return new PendingAction(actionId, sessionId, messageId, memberId, tool, title, args, preview, now,
                now.plus(TTL), ActionStatus.PENDING, null);
    }

    public boolean expiredAt(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    /** 确认前校验：本人、未过期、仍待确认。 */
    public void assertConfirmable(Long member, LocalDateTime now) {
        if (!memberId.equals(member)) throw new DomainException("无权操作这条待确认动作");
        if (status != ActionStatus.PENDING) throw new DomainException("这条动作已" + label(status));
        if (expiredAt(now)) throw new DomainException("已过期，请重新发起");
    }

    public PendingAction withStatus(ActionStatus s) {
        return new PendingAction(actionId, sessionId, messageId, memberId, tool, title, args, preview, createdAt,
                expiresAt, s, undoToken);
    }

    /** 执行完成：记下撤销凭据。 */
    public PendingAction done(String token) {
        return new PendingAction(actionId, sessionId, messageId, memberId, tool, title, args, preview, createdAt,
                expiresAt, ActionStatus.DONE, token);
    }

    private static String label(ActionStatus s) {
        return switch (s) {
            case DONE -> "执行";
            case CANCELLED -> "取消";
            case EXPIRED -> "过期";
            case UNDONE -> "撤销";
            case SUPERSEDED -> "被新卡片替代";
            case PENDING -> "待确认";
        };
    }
}
