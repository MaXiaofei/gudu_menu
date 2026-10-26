package com.gudu.xsd.modules.notification;

/**
 * 通知收件人：<b>显式带 id 的种类</b>。账号 id（account.id）与成员档案 id（member.id）是两张表各自的自增，
 * 取值范围虽由 V91 错开，但语义不同、归属/合并/注销的处理也不同，所以发送方必须说清楚发给「账号」还是「档案」。
 *
 * @param accountId 账号收件（与 profileId 二选一）
 * @param profileId 档案收件（与 accountId 二选一）
 */
public record NotificationRecipient(Long accountId, Long profileId) {

    public NotificationRecipient {
        if ((accountId == null) == (profileId == null)) {
            throw new IllegalArgumentException("通知收件人必须且只能指定账号或档案之一");
        }
    }

    /** 发给账号（跟着人走，切换就餐成员 / 家庭都能看到）。 */
    public static NotificationRecipient account(Long accountId) {
        return new NotificationRecipient(accountId, null);
    }

    /** 发给成员档案（跟着就餐成员走）。 */
    public static NotificationRecipient profile(Long profileId) {
        return new NotificationRecipient(null, profileId);
    }
}
