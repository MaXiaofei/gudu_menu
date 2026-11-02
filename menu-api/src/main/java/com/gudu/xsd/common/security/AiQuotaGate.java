package com.gudu.xsd.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;

/**
 * AI 次数闸门：按<b>登录账号</b>（account.id）计每日次数，不按成员档案——档案可以无限新建，按它计等于没限制。
 * Redis 不可用时一律拒绝（fail-closed）：AI 要花钱，计数设施坏了宁可暂停也不放开。
 */
public interface AiQuotaGate {

    /** 计一次；超限抛 429，计数设施故障抛 503（均带可读提示）。 */
    void require(AiQuotaCategory category, Long accountId);

    /** 计一次并返回是否放行（超限或计数设施故障都返回 false，不抛异常；咕嘟对话用它退回规则回复）。 */
    boolean tryConsume(AiQuotaCategory category, Long accountId);

    /**
     * 当前登录账号：先取 Sa-Token 登录态，异步线程（咕嘟 SSE 子线程）没有登录态则取租户上下文里的 accountId，
     * 都取不到才退回 {@code fallback}（单测 / 后台任务）。
     */
    static Long currentAccountId(Long fallback) {
        try {
            if (StpUtil.isLogin()) return StpUtil.getLoginIdAsLong();
        } catch (RuntimeException ignored) { // 无 Web 上下文
        }
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null && ctx.accountId() != null) return ctx.accountId();
        return fallback;
    }
}
