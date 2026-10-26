package com.gudu.xsd.modules.member;

import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 平台超管判定（multi-tenant-family 起以 account.platform_role = SUPER_ADMIN 为准，loginId = account.id）。
 * 供 {@link AdminGuardAspect} 与成员接口的字段级权限使用。与家庭内 OWNER/ADMIN 无关。
 */
@Component
@RequiredArgsConstructor
public class AdminChecker {

    private final AccountQueryApi accountQuery;

    /** @param accountId 登录账号 id（StpUtil loginId） */
    public boolean isAdmin(Long accountId) {
        return accountId != null && accountQuery.isSuperAdmin(accountId);
    }

    /** 【旧列兼容】成员档案上的超管标记（仅展示用，不作鉴权依据）。 */
    public static boolean isAdmin(Member m) {
        return m != null && Integer.valueOf(1).equals(m.getIsAdmin());
    }

    /** 纯成员档案：没有关联登录账号（宝宝、老人）。 */
    public static boolean isProfileOnly(Member m) {
        return m != null && m.getAccountId() == null && m.getPasswordHash() == null && m.getOpenid() == null;
    }
}
