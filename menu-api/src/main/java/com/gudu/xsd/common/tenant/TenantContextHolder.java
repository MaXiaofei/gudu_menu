package com.gudu.xsd.common.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 租户上下文持有者（ThreadLocal）。Web 请求由 TenantInterceptor 设置并在请求结束清理；
 * 后台任务 / 异步线程必须显式 {@link #runAs} / {@link #wrap}，否则访问家庭域表直接抛异常（防漏设）。
 *
 * <p>{@link #callAsSystem}：平台级操作（账号合并、注销、运营统计）显式绕过租户过滤，
 * 调用方必须自带 family_id 条件。仅限 identity / family 上下文与运营后台使用。
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static Optional<TenantContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    /** 当前上下文；未设置返回 null。 */
    public static TenantContext get() {
        return CONTEXT.get();
    }

    public static void set(TenantContext ctx) {
        if (ctx == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(ctx);
        }
    }

    public static void clear() {
        CONTEXT.remove();
        SYSTEM.remove();
    }

    /** 当前家庭 id；无上下文或游客抛 {@link TenantAccessDeniedException}。 */
    public static Long requireFamilyId() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null || ctx.familyId() == null) {
            throw new TenantAccessDeniedException("请先登录并选择家庭");
        }
        return ctx.familyId();
    }

    /** 当前家庭 id；无则 null。 */
    public static Long familyIdOrNull() {
        TenantContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.familyId();
    }

    /** 当前登录账号在本家庭的成员档案 id；无则 null。 */
    public static Long profileIdOrNull() {
        TenantContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.profileId();
    }

    public static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM.get());
    }

    public static <T> T callAs(TenantContext ctx, Supplier<T> body) {
        TenantContext prevCtx = CONTEXT.get();
        try {
            set(ctx);
            return body.get();
        } finally {
            set(prevCtx);
        }
    }

    public static void runAs(TenantContext ctx, Runnable body) {
        callAs(ctx, () -> {
            body.run();
            return null;
        });
    }

    /** 以某个家庭身份运行（定时任务按家庭逐个处理、聚餐访客按菜单所属家庭）。 */
    public static void runAsFamily(Long familyId, Runnable body) {
        runAs(TenantContext.ofFamily(familyId), body);
    }

    public static <T> T callAsFamily(Long familyId, Supplier<T> body) {
        return callAs(TenantContext.ofFamily(familyId), body);
    }

    /** 绕过租户过滤（平台级操作）；SQL 必须自带 family_id 条件。 */
    public static <T> T callAsSystem(Supplier<T> body) {
        Boolean prev = SYSTEM.get();
        try {
            SYSTEM.set(Boolean.TRUE);
            return body.get();
        } finally {
            restoreSystem(prev);
        }
    }

    public static void runAsSystem(Runnable body) {
        callAsSystem(() -> {
            body.run();
            return null;
        });
    }

    /** 捕获当前上下文，供提交到线程池的任务在子线程恢复（SSE、异步识别等）。 */
    public static Runnable wrap(Runnable task) {
        TenantContext captured = CONTEXT.get();
        boolean system = isSystem();
        return () -> {
            if (system) {
                runAsSystem(() -> runAs(captured, task));
            } else {
                runAs(captured, task);
            }
        };
    }

    private static void restoreSystem(Boolean prev) {
        if (prev == null) {
            SYSTEM.remove();
        } else {
            SYSTEM.set(prev);
        }
    }
}
