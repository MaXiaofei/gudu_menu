package com.gudu.xsd.modules.community.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/**
 * 厨房圈紧急关停（A.12）：开关关闭时 {@code /community/**} 读写接口统一返回业务码
 * {@link #CODE_COMMUNITY_SUSPENDED} +「厨房圈暂停服务」（HTTP 200，与限流/权限等业务码同一通道，
 * 客户端据此显示暂停提示而非报错）。管理后台 {@code /admin/community/**} 不经过本拦截器。
 */
public class CommunitySuspendGuard implements HandlerInterceptor {

    public static final int CODE_COMMUNITY_SUSPENDED = 5031;
    public static final String MSG_COMMUNITY_SUSPENDED = "厨房圈暂停服务";

    /** 开关读取（CommunitySwitchAppService#isEnabled，带短缓存）。 */
    private final BooleanSupplier enabled;
    private final ObjectMapper objectMapper;

    public CommunitySuspendGuard(BooleanSupplier enabled, ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
    }

    /** 拦截范围（纯规则）：用户侧社区接口；管理后台路径不在内。 */
    static boolean appliesTo(String path) {
        return path != null && (path.equals("/community") || path.startsWith("/community/"));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!appliesTo(path) || enabled.getAsBoolean()) return true;
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new R<>(CODE_COMMUNITY_SUSPENDED, MSG_COMMUNITY_SUSPENDED, null)));
        return false;
    }
}
