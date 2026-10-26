package com.gudu.xsd.modules.notification;

import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并：账号级通知（反馈回复、厨房圈互动，recipient_account_id）随账号由 W 改指 P，合并后用哪种方式登录都能看到。
 * 档案级通知（recipient_profile_id）随档案走，不在这里处理。与合并同事务同步执行，幂等。
 */
@Component
@RequiredArgsConstructor
public class NotificationAccountMergedListener {

    private final NotificationService notifications;

    @EventListener
    public void on(AccountMerged e) {
        notifications.reassignAccount(e.sourceAccountId(), e.targetAccountId());
    }
}
