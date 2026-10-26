package com.gudu.xsd.modules.notification;

/**
 * 通知载荷（与通道实现解耦，业务侧只构造这个）。
 *
 * @param recipient 收件人（显式区分账号 / 档案，见 {@link NotificationRecipient}）
 * @param refId     点击跳转目标 id（可空；如 feedback_reply → 反馈 id）
 */
public record NotificationPayload(NotificationRecipient recipient, String type, String title, String content,
                                  String refId) {

    public NotificationPayload(NotificationRecipient recipient, String type, String title, String content) {
        this(recipient, type, title, content, null);
    }
}
