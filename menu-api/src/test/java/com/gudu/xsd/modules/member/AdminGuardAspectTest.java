package com.gudu.xsd.modules.member;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 平台超管守卫：按登录账号判定，非超管 403 且不执行。 */
class AdminGuardAspectTest {

    private final AdminChecker checker = mock(AdminChecker.class);
    private final AdminGuardAspect aspect = new AdminGuardAspect(checker);

    @Test
    void 非超管_403() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(checker.isAdmin(10L)).thenReturn(false);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            assertThatThrownBy(() -> aspect.check(pjp))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(403);
            verify(pjp, never()).proceed();
        }
    }

    @Test
    void 超管_放行() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");
        when(checker.isAdmin(1L)).thenReturn(true);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertThat(aspect.check(pjp)).isEqualTo("OK");
        }
    }

    @Test
    void 纯档案判定() {
        Member baby = new Member();
        assertThat(AdminChecker.isProfileOnly(baby)).isTrue();
        Member wx = new Member();
        wx.setOpenid("oX");
        assertThat(AdminChecker.isProfileOnly(wx)).isFalse();
        Member pwd = new Member();
        pwd.setPasswordHash("h");
        assertThat(AdminChecker.isProfileOnly(pwd)).isFalse();
        assertThat(AdminChecker.isProfileOnly(null)).isFalse();
    }
}
