package com.gudu.xsd.modules.family.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.common.tenant.TenantAccessDeniedException;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.family.application.FamilyAppService;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.SessionFamily;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Optional;

/**
 * 租户拦截器（design §4.1，在 Sa-Token 之后）：建立本次请求的 {@link TenantContext}。
 *
 * <ol>
 *   <li>已登录且会话有当前家庭 → <b>每次请求</b>校验账号仍是该家庭成员（Redis 缓存 5 分钟 + 事件失效）；
 *       非成员 → 清会话家庭，403 FAMILY_ACCESS_DENIED（/auth、/account、/family 例外，以便重新选择家庭）；</li>
 *   <li>已登录但未选家庭（旧前端 / 旧 token）→ 自动落到上次使用 / 唯一家庭，无家庭则建「我的家」；</li>
 *   <li>未登录 → 游客上下文（家庭域表读为空、写拒绝）；仅在关闭登录校验的本地/测试环境
 *       且配置了 {@code gudu.tenant.anonymous-family-id} 时以该家庭身份运行（roadmap 决策 5：测试环境放开）。</li>
 * </ol>
 */
public class TenantInterceptor implements HandlerInterceptor {

    /** 家庭失效时仍放行的路径前缀：登录、账号、家庭选择本身。 */
    static final List<String> FAMILY_OPTIONAL_PREFIXES = List.of("/auth/", "/account", "/family");

    private final FamilyQueryApi query;
    private final FamilyAppService familyApp;
    private final Long anonymousFamilyId;

    public TenantInterceptor(FamilyQueryApi query, FamilyAppService familyApp, Long anonymousFamilyId) {
        this.query = query;
        this.familyApp = familyApp;
        this.anonymousFamilyId = anonymousFamilyId;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        TenantContextHolder.clear();
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
        if (!isLogin()) {
            TenantContextHolder.set(anonymousFamilyId == null ? TenantContext.guest()
                    : TenantContext.ofFamily(anonymousFamilyId));
            return true;
        }
        long accountId = StpUtil.getLoginIdAsLong();
        String path = pathOf(req);
        Long familyId = TenantSession.familyId();
        if (familyId != null) {
            Optional<String> role = query.roleOf(familyId, accountId);
            if (role.isPresent()) {
                TenantContextHolder.set(new TenantContext(familyId, accountId, TenantSession.currentMemberId(),
                        role.get()));
                return true;
            }
            TenantSession.clearFamily();
            if (!familyOptional(path)) {
                throw new TenantAccessDeniedException("你已不在该家庭中，请重新选择家庭");
            }
            TenantContextHolder.set(new TenantContext(null, accountId, null, null));
            return true;
        }
        // 未选家庭：落到上次使用 / 唯一家庭（兼容旧前端），没有就自动建「我的家」
        SessionFamily sf = familyApp.resolveSession(accountId, null);
        TenantSession.bind(sf.familyId(), sf.profileId());
        TenantContextHolder.set(new TenantContext(sf.familyId(), accountId, sf.profileId(), sf.role()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    static boolean familyOptional(String path) {
        return FAMILY_OPTIONAL_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private static String pathOf(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }

    private static boolean isLogin() {
        try {
            return StpUtil.isLogin();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
