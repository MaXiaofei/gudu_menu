package com.gudu.xsd.modules.notification;

import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 账号注销后续匿名化（隐私政策 §七.6）：删除发给该账号的通知（跨家庭，以系统模式执行）。幂等。
 *
 * <p>通知收件人分两列：{@code recipient_account_id}（账号级）按账号 id 删；{@code recipient_profile_id}（档案级）
 * 按<b>本人名下档案 id</b>（{@link FamilyQueryApi#profileIdsOf}）删。账号 id 与档案 id 是两张表各自的自增，
 * 绝不能拿账号 id 去比档案列（撞号会误删别人的通知）。
 */
@Component
@RequiredArgsConstructor
public class NotificationAccountErasureStep implements AccountErasureStep {

    private final NotificationService notifications;
    private final FamilyQueryApi families;

    @Override
    public String name() {
        return "通知删除";
    }

    @Override
    public String erase(Long accountId) {
        List<Long> profileIds = families.profileIdsOf(accountId);
        return "删除 " + notifications.deleteForAccount(accountId, profileIds) + " 条";
    }
}
