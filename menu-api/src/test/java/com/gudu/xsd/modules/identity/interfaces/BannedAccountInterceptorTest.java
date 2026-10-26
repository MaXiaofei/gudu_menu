package com.gudu.xsd.modules.identity.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** F.1：封禁账号写接口 423；读接口、登出、未登录不拦。 */
class BannedAccountInterceptorTest {

    final AccountBanAppService bans = mock(AccountBanAppService.class);
    final BannedAccountInterceptor it = new BannedAccountInterceptor(bans);

    static MockHttpServletRequest req(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, "/gudu" + uri);
        r.setContextPath("/gudu");
        return r;
    }

    @Test
    void 封禁账号写请求_423() {
        doThrow(BizException.accountBanned("刷广告")).when(bans).assertNotBanned(5L);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(5L);
            assertThatThrownBy(() -> it.preHandle(req("POST", "/menu"), new MockHttpServletResponse(), null))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(423);
            assertThat(it.preHandle(req("GET", "/menu"), new MockHttpServletResponse(), null)).isTrue();
            assertThat(it.preHandle(req("POST", "/auth/logout"), new MockHttpServletResponse(), null)).isTrue();
        }
    }

    @Test
    void 未登录_不查封禁() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertThat(it.preHandle(req("POST", "/auth/wx-login"), new MockHttpServletResponse(), null)).isTrue();
            verify(bans, never()).assertNotBanned(any());
        }
    }
}
