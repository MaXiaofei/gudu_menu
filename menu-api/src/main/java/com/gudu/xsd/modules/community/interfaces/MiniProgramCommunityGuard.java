package com.gudu.xsd.modules.community.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.web.ClientSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 微信小程序不开放厨房圈互动（mini-release-compliance A.1）。
 *
 * <p>来自小程序（{@code X-Client: mp-weixin}）的厨房圈写请求——发帖、评论、点赞、关注、举报、
 * 申诉、复刻菜谱、AI 文案——一律 HTTP 403 + 业务码 {@link #CODE_COMMUNITY_CLOSED}；
 * 读接口与 App / H5 不受影响。删除自己的帖子保留（用户删除自己内容的权利）。
 */
public class MiniProgramCommunityGuard implements HandlerInterceptor {

    /** 业务码：该客户端未开放厨房圈写操作（区别于通用 403 无权限）。 */
    public static final int CODE_COMMUNITY_CLOSED = 4031;
    public static final String MSG_COMMUNITY_CLOSED = "小程序暂未开放厨房圈发布与互动";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final String OWN_POST_DELETE = "/community/posts/{id:\\d+}";

    private final ObjectMapper objectMapper;

    public MiniProgramCommunityGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 纯规则（便于单测）：该请求是否被拦截。path 为去掉 context-path 后的路径。 */
    static boolean blocks(String method, String path, String clientHeader) {
        if (!ClientSource.isMiniProgram(clientHeader)) return false;
        if (path == null || !(path.equals("/community") || path.startsWith("/community/"))) return false;
        String m = method == null ? "" : method.toUpperCase();
        if (m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS")) return false;
        return !(m.equals("DELETE") && MATCHER.match(OWN_POST_DELETE, path));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!blocks(request.getMethod(), path, request.getHeader(ClientSource.HEADER))) return true;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new R<>(CODE_COMMUNITY_CLOSED, MSG_COMMUNITY_CLOSED, null)));
        return false;
    }
}
