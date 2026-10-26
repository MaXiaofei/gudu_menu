package com.gudu.xsd.modules.identity.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 封禁账号写接口拦截（mini-release-compliance F.1）：已登录账号发起写请求（非 GET/HEAD/OPTIONS）时，
 * 若已被封禁 → 423 ACCOUNT_BANNED。封禁时已下线全部登录态，本拦截兜底「封禁前已签发、尚未失效的 token」。
 * 登出放行（让客户端能正常退出）。
 */
public class BannedAccountInterceptor implements HandlerInterceptor {

    static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    static final Set<String> ALWAYS_ALLOWED = Set.of("/auth/logout");

    private final AccountBanAppService bans;

    public BannedAccountInterceptor(AccountBanAppService bans) {
        this.bans = bans;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if (READ_METHODS.contains(req.getMethod().toUpperCase(java.util.Locale.ROOT))) return true;
        if (ALWAYS_ALLOWED.contains(pathOf(req))) return true;
        Long accountId = loginId();
        if (accountId != null) bans.assertNotBanned(accountId);
        return true;
    }

    private static Long loginId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String pathOf(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }
}
