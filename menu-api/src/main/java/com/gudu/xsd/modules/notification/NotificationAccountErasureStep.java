package com.gudu.xsd.modules.notification;

import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 账号注销后续匿名化（隐私政策 §七.6）：删除发给该账号的通知（跨家庭，以系统模式执行）。幂等。 */
@Component
@RequiredArgsConstructor
public class NotificationAccountErasureStep implements AccountErasureStep {

    private final NotificationService notifications;

    @Override
    public String name() {
        return "通知删除";
    }

    @Override
    public String erase(Long accountId) {
        return "删除 " + notifications.deleteByMember(accountId) + " 条";
    }
}
