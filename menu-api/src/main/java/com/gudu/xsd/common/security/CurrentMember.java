package com.gudu.xsd.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.tenant.TenantContextHolder;

/**
 * 当前就餐成员（member.id = 成员档案）。multi-tenant-family 起 loginId 是 account.id，
 * <b>不能</b>再当作 member.id 使用：按成员归属的数据（做菜记录、点评、食记、收藏、通知、草稿…）一律取这里。
 *
 * <p>取值顺序：本次请求的 TenantContext.profileId（拦截器从 token 会话装入）→ token 会话 currentMemberId → null。
 */
public final class CurrentMember {

    private CurrentMember() {
    }

    public static Long id() {
        Long fromCtx = TenantContextHolder.profileIdOrNull();
        if (fromCtx != null) return fromCtx;
        try {
            return StpUtil.isLogin() ? TenantSession.currentMemberId() : null;
        } catch (RuntimeException e) { // 无 Web 上下文（单测 / 后台线程）
            return null;
        }
    }
}
