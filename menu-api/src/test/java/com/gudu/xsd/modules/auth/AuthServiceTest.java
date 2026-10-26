package com.gudu.xsd.modules.auth;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.family.application.FamilyAppService;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.FamilySummary;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.SessionFamily;
import com.gudu.xsd.modules.identity.application.AccountAppService;
import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import com.gudu.xsd.modules.identity.application.dto.AccountView;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthService（multi-tenant-family A.4）：基于 account 认证，loginId = account.id；
 * 登录后解析当前家庭写入 token 会话，响应带 families[] / currentFamilyId / currentMemberId。
 */
class AuthServiceTest {

    private final AccountAppService accounts = mock(AccountAppService.class);
    private final FamilyAppService familyApp = mock(FamilyAppService.class);
    private final FamilyQueryApi familyQuery = mock(FamilyQueryApi.class);
    private final WxClient wx = mock(WxClient.class);
    private final AccountBanAppService bans = mock(AccountBanAppService.class);
    private final AuthService svc = new AuthService(accounts, familyApp, familyQuery, wx, bans);

    private static AccountView acc(long id, String nick, String role) {
        return new AccountView(id, nick, null, "138****0001", false, true, role, "ACTIVE");
    }

    private Map<String, Object> withStp(java.util.function.Supplier<Map<String, Object>> body, SaSession session) {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenSession).thenReturn(session);
            stp.when(StpUtil::getTokenValue).thenReturn("tok-abc");
            return body.get();
        }
    }

    @Test
    void 手机号登录_返回token_家庭列表_当前家庭与本人档案() {
        when(accounts.authenticate("13800000001", "pw123")).thenReturn(acc(10L, "测试员", "USER"));
        when(familyApp.resolveSession(10L, null)).thenReturn(new SessionFamily(3L, 31L, "OWNER"));
        when(familyQuery.familiesOf(10L)).thenReturn(List.of(new FamilySummary(3L, "我的家", "OWNER", 1)));
        SaSession session = mock(SaSession.class);

        Map<String, Object> r = withStp(() -> svc.login(new LoginDTO("13800000001", "pw123")), session);

        assertThat(r).containsEntry("token", "tok-abc").containsEntry("accountId", 10L)
                .containsEntry("nickname", "测试员").containsEntry("isAdmin", false)
                .containsEntry("currentFamilyId", 3L).containsEntry("currentMemberId", 31L);
        assertThat((List<?>) r.get("families")).hasSize(1);
        verify(session).set("familyId", 3L);
        verify(session).set("currentMemberId", 31L);
    }

    @Test
    void 认证失败_异常透传_不签发token() {
        when(accounts.authenticate(any(), any())).thenThrow(new BizException("用户名或密码错误"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThatThrownBy(() -> svc.login(new LoginDTO("x", "y"))).hasMessageContaining("用户名或密码错误");
            stp.verify(() -> StpUtil.login(any()), never());
        }
    }

    @Test
    void 后台登录_非超管_403且不签发token() {
        when(accounts.authenticate("13800000001", "pw123")).thenReturn(acc(10L, "测试员", "USER"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThatThrownBy(() -> svc.login(new LoginDTO("13800000001", "pw123", "admin")))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(403);
            stp.verify(() -> StpUtil.login(any()), never());
        }
    }

    @Test
    void 后台登录_超管_成功_会话记录admin场景() {
        when(accounts.authenticate("admin", "admin123")).thenReturn(acc(99L, "掌勺人", "SUPER_ADMIN"));
        when(familyApp.resolveSession(99L, null)).thenReturn(new SessionFamily(1L, 99L, "OWNER"));
        SaSession session = mock(SaSession.class);
        Map<String, Object> r = withStp(() -> svc.login(new LoginDTO("admin", "admin123", "admin")), session);
        assertThat(r).containsEntry("isAdmin", true);
        verify(session).set("loginScope", "admin");
    }

    @Test
    void 微信登录_openid交给账号服务_带邀请码() {
        when(wx.code2session("c1")).thenReturn("oid-1");
        when(accounts.loginOrRegisterByWechat("oid-1", null, "ABCDEFGH")).thenReturn(acc(5L, "微信用户", "USER"));
        when(familyApp.resolveSession(5L, null)).thenReturn(new SessionFamily(8L, 81L, "MEMBER"));
        Map<String, Object> r = withStp(() -> svc.wxLogin("c1", "ABCDEFGH"), mock(SaSession.class));
        assertThat(r).containsEntry("accountId", 5L).containsEntry("currentFamilyId", 8L);
    }

    @Test
    void 微信登录_code2session失败_抛业务异常() {
        when(wx.code2session("bad")).thenThrow(new BizException("微信登录失败(40029)：invalid code"));
        assertThatThrownBy(() -> svc.wxLogin("bad", null)).hasMessageContaining("40029");
        verify(accounts, never()).loginOrRegisterByWechat(any(), any(), any());
    }

    @Test
    void 账号合并后重签_沿用合并前的当前家庭() {
        when(familyApp.resolveSession(eq(10L), eq(8L))).thenReturn(new SessionFamily(8L, 82L, "MEMBER"));
        Map<String, Object> r = withStp(() -> svc.establish(acc(10L, "老张", "USER"), null, 8L), mock(SaSession.class));
        assertThat(r).containsEntry("currentFamilyId", 8L).containsEntry("currentMemberId", 82L);
    }

    @Test
    void 后台登录_平台运营_可登录_响应isOperator() {
        when(accounts.authenticate("13800000002", "pw123")).thenReturn(acc(12L, "运营小李", "OPERATOR"));
        when(familyApp.resolveSession(12L, null)).thenReturn(new SessionFamily(4L, 41L, "OWNER"));
        Map<String, Object> r = withStp(() -> svc.login(new LoginDTO("13800000002", "pw123", "admin")),
                mock(SaSession.class));
        assertThat(r).containsEntry("isAdmin", false).containsEntry("isOperator", true)
                .containsEntry("platformRole", "OPERATOR");
    }

    @Test
    void 封禁账号_手机号与微信登录均被拒_423_不签发token() {
        when(accounts.authenticate("13800000001", "pw123")).thenReturn(acc(10L, "测试员", "USER"));
        when(wx.code2session("c1")).thenReturn("oid-1");
        when(accounts.loginOrRegisterByWechat("oid-1", null, null)).thenReturn(acc(10L, "测试员", "USER"));
        doThrow(BizException.accountBanned("发布违规内容")).when(bans).assertNotBanned(10L);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThatThrownBy(() -> svc.login(new LoginDTO("13800000001", "pw123")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("发布违规内容")
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(BizException.ACCOUNT_BANNED);
            assertThatThrownBy(() -> svc.wxLogin("c1", null))
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(BizException.ACCOUNT_BANNED);
            stp.verify(() -> StpUtil.login(any()), never());
        }
    }
}
