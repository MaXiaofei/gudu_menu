package com.gudu.xsd.common.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;

/**
 * 登录会话里的家庭身份（Sa-Token session）：
 * <ul>
 *   <li>{@value #FAMILY_ID}：当前家庭；</li>
 *   <li>{@value #CURRENT_MEMBER_ID}：当前就餐成员档案（member.id，兼容旧代码读 currentMemberId）；</li>
 *   <li>{@value #LOGIN_SCOPE}：admin = 管理后台登录（公共菜谱库写入以此 + 平台角色判定）。</li>
 * </ul>
 * loginId = account.id（multi-tenant-family 起账号与成员档案分离）。
 */
public final class TenantSession {

    public static final String FAMILY_ID = "familyId";
    public static final String CURRENT_MEMBER_ID = "currentMemberId";
    public static final String LOGIN_SCOPE = "loginScope";
    public static final String SCOPE_ADMIN = "admin";

    private TenantSession() {
    }

    public static void bind(Long familyId, Long profileId) {
        SaSession s = StpUtil.getTokenSession();
        s.set(FAMILY_ID, familyId);
        if (profileId == null) {
            s.delete(CURRENT_MEMBER_ID);
        } else {
            s.set(CURRENT_MEMBER_ID, profileId);
        }
    }

    public static Long familyId() {
        Object v = StpUtil.getTokenSession().get(FAMILY_ID);
        return v == null ? null : Long.valueOf(v.toString());
    }

    public static Long currentMemberId() {
        Object v = StpUtil.getTokenSession().get(CURRENT_MEMBER_ID);
        return v == null ? null : Long.valueOf(v.toString());
    }

    public static void setCurrentMember(Long profileId) {
        StpUtil.getTokenSession().set(CURRENT_MEMBER_ID, profileId);
    }

    public static void clearFamily() {
        SaSession s = StpUtil.getTokenSession();
        s.delete(FAMILY_ID);
        s.delete(CURRENT_MEMBER_ID);
    }

    public static void setScope(String scope) {
        if (scope == null) {
            StpUtil.getTokenSession().delete(LOGIN_SCOPE);
        } else {
            StpUtil.getTokenSession().set(LOGIN_SCOPE, scope);
        }
    }

    public static boolean isAdminScope() {
        try {
            return StpUtil.isLogin() && SCOPE_ADMIN.equals(StpUtil.getTokenSession().get(LOGIN_SCOPE));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
