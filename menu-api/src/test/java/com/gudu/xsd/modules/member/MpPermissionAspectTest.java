package com.gudu.xsd.modules.member;

import cn.dev33.satoken.session.SaSession;
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
    private final MpPermissionAspect aspect = new MpPermissionAspect(permSvc);

    @Test
    void 未选就餐成员_放行() throws Throwable {
        // session 不存在 → currentMemberId 返回 null → 应放行
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");

        MpPerm anno = permAnnotation("dish.create");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getSession(false)).thenReturn(null);
            Object result = aspect.check(pjp, anno);
            assertThat(result).isEqualTo("OK");
            verify(pjp).proceed();
        }
    }

    @Test
    void 已选成员_无对应权限_切面抛BizException拒绝() throws Throwable {
        // session.currentMemberId = 1L;但 memberMapper=null → resolveByMemberId 返回空集 → 拒绝
        // 此测试验证「拒绝」分支:切面真拦截并抛 BizException("无此功能权限"),不 proceed
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("DONE");

        SaSession session = mock(SaSession.class);
        when(session.get("currentMemberId")).thenReturn(1L);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getSession(false)).thenReturn(session);
            assertThatThrownBy(() -> aspect.check(pjp, permAnnotation("dish.create")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无此功能权限");
            verify(pjp, never()).proceed();
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
