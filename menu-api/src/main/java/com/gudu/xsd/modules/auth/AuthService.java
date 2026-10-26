package com.gudu.xsd.modules.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.modules.family.application.FamilyAppService;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.SessionFamily;
import com.gudu.xsd.modules.identity.application.AccountAppService;
import com.gudu.xsd.modules.identity.application.dto.AccountView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录（multi-tenant-family A.4）：基于 {@code account} 认证，loginId = account.id；
 * 登录后解析当前家庭（上次使用 / 唯一家庭 / 自动建「我的家」）写入 token 会话。
 *
 * <p>响应：token、nickname、isAdmin（平台超管）、accountId、families[]、currentFamilyId、
 * currentMemberId（本人在当前家庭的成员档案 id，兼容旧前端「登录即定就餐成员」）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountAppService accounts;
    private final FamilyAppService familyApp;
    private final FamilyQueryApi familyQuery;
    private final WxClient wxClient;

    /** 管理后台登录场景。 */
    static final String SCOPE_ADMIN = TenantSession.SCOPE_ADMIN;

    public Map<String, Object> login(LoginDTO dto) {
        AccountView a = accounts.authenticate(dto.getUsername(), dto.getPassword());
        if (SCOPE_ADMIN.equals(dto.getScope()) && !a.superAdmin()) {
            throw BizException.forbidden("无后台权限");
        }
        return establish(a, SCOPE_ADMIN.equals(dto.getScope()) ? SCOPE_ADMIN : null, null);
    }

    /**
     * 微信小程序静默登录：wx.login 的 code → openid → 查/建账号 → 发券。
     * 新 openid 自动注册：带有效 inviteCode 则直接加入邀请家庭，否则自动建「我的家」。
     * 已并入手机号账号的 openid 直接登录到手机号账号。
     */
    public Map<String, Object> wxLogin(String code, String inviteCode) {
        String openid = wxClient.code2session(code);
        AccountView a = accounts.loginOrRegisterByWechat(openid, null, inviteCode);
        return establish(a, null, null);
    }

    /** 登录并绑定当前家庭（preferredFamilyId 非空且仍是成员时沿用，账号合并后沿用合并前的当前家庭）。 */
    public Map<String, Object> establish(AccountView a, String scope, Long preferredFamilyId) {
        StpUtil.login(a.id());
        TenantSession.setScope(scope);
        SessionFamily sf = familyApp.resolveSession(a.id(), preferredFamilyId);
        TenantSession.bind(sf.familyId(), sf.profileId());
        Map<String, Object> out = new HashMap<>();
        out.put("token", StpUtil.getTokenValue());
        out.put("accountId", a.id());
        out.put("nickname", a.nickname());
        out.put("isAdmin", a.superAdmin());
        out.put("platformRole", a.platformRole());
        out.put("families", familyQuery.familiesOf(a.id()));
        out.put("currentFamilyId", sf.familyId());
        out.put("currentMemberId", sf.profileId());
        return out;
    }
}
