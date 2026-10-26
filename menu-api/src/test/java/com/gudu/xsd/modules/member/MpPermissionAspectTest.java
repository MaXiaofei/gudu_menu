package com.gudu.xsd.modules.member;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 权限切面单元测试:验证切面核心拦截逻辑(拒绝/放行)。
 *
 * <p>用 MockedStatic 桩 StpUtil.getSession,控制 currentMemberId;
 * permSvc 用真实 MpPermissionService(纯函数)而非 mock,顺带覆盖 resolveByMemberId 链路。
 * joinPoint.proceed() 用 mock 验证是否放行执行。
 */
class MpPermissionAspectTest {

    /** 真实 permSvc(纯函数);memberMapper=null,走 resolvePermissions 不触 DB。 */
    private final MpPermissionService permSvc = new MpPermissionService(null);
    private final MpPermissionAspect aspect = new MpPermissionAspect(permSvc, true);

    @Test
    void 未登录_生产强制登录_抛401且不执行() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            stp.when(StpUtil::checkLogin).thenThrow(NotLoginException.newInstance("login", "-1", "未登录", null));
            assertThatThrownBy(() -> aspect.check(pjp, permAnnotation("dish.create")))
                    .isInstanceOf(NotLoginException.class);
            verify(pjp, never()).proceed();
        }
    }

    @Test
    void 未登录_测试环境放开登录_放行() throws Throwable {
        MpPermissionAspect open = new MpPermissionAspect(permSvc, false);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertThat(open.check(pjp, permAnnotation("dish.create"))).isEqualTo("OK");
        }
    }

    @Test
    void 已登录_账号无对应权限_403拒绝() throws Throwable {
        // loginId=1;memberMapper=null → resolveByMemberId 返回空集 → 拒绝,不 proceed
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertThatThrownBy(() -> aspect.check(pjp, permAnnotation("dish.create")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无此功能权限")
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(403);
            verify(pjp, never()).proceed();
        }
    }

    @Test
    void 权限按登录账号判定_不读当前就餐成员() throws Throwable {
        // 账号 2 是超管;即使 session.currentMemberId 被切成别人,判定仍用 loginId
        // 账号 2 是平台超管（account.platform_role=SUPER_ADMIN）
        com.gudu.xsd.modules.identity.application.AccountQueryApi accounts =
                mock(com.gudu.xsd.modules.identity.application.AccountQueryApi.class);
        when(accounts.isSuperAdmin(2L)).thenReturn(true);
        MpPermissionService svc = new MpPermissionService(null, accounts, null);
        MpPermissionAspect a = new MpPermissionAspect(svc, true);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThat(a.check(pjp, permAnnotation("ai.use"))).isEqualTo("OK");
            stp.verify(() -> StpUtil.getSession(false), never());
        }
    }

    @Test
    void resolvePermissions纯函数_普通成员无dish_create() {
        // 佐证:普通成员(34)解析后确实不含 dish.create
        Set<String> perms = permSvc.resolvePermissions("34", null);
        assertThat(perms).doesNotContain("dish.create");
    }

    @Test
    void resolvePermissions纯函数_普通成员勾选后含menu_plan() {
        Set<String> perms = permSvc.resolvePermissions("34", List.of("menu.plan"));
        assertThat(perms).contains("menu.plan");
    }

    /** 反射构造 @MpPerm 注解实例(避免依赖具体接口签名)。 */
    private MpPerm permAnnotation(String key) {
        return new MpPerm() {
            @Override public String value() { return key; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return MpPerm.class; }
        };
    }
}
