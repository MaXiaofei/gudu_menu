package com.gudu.xsd.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Sa-Token 拦截器：默认全部接口强制登录。
 *
 * <ul>
 *   <li>{@link #PUBLIC_PATHS}：登录、微信回调、接口文档、上传文件、聚餐访客入口——任何方法免登录；</li>
 *   <li>{@link #GUEST_READ_PATHS}：公共只读接口（菜谱浏览/搜索/详情、字典、食材）——仅 GET（及菜谱 POST 搜索）免登录，
 *       满足微信小程序审核「未登录可浏览」要求；</li>
 *   <li>auth.login-check=false 仅供本地/测试环境放开（生产 compose 强制 true）。</li>
 * </ul>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/wx-login", // 微信小程序静默登录
            "/auth/wx-echo",  // 微信公众平台「服务器配置」地址验证
            "/wx/push",       // 微信消息推送（图片内容安全异步结果），靠签名校验而非登录态
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico",
            "/uploads/**",
            // 聚餐：邀请链接免登录（H5 访客入口/加入），清单与加菜走身份头（member 或 X-Guest-Key）
            "/invite/**",
            "/menu/*/together",
            "/menu/*/together/**",
            "/together.html"
    );

    static final List<String> GUEST_READ_PATHS = List.of(
            "/dish",
            "/dish/search",
            "/dish/{id:\\d+}",
            "/dish/{id:\\d+}/nutrition",
            "/dict",
            "/dict/**",
            "/ingredient",
            "/ingredient/{id:\\d+}",
            "/ingredient/{id:\\d+}/nutrition"
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Value("${auth.login-check:true}")
    private boolean loginCheck;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRequest req = SaHolder.getRequest();
            if (loginCheck && requiresLogin(req.getMethod(), req.getRequestPath())) {
                StpUtil.checkLogin();
            }
        })).addPathPatterns("/**");
    }

    /** 该请求是否需要登录（与 loginCheck 开关无关的纯规则，便于单测）。 */
    static boolean requiresLogin(String method, String path) {
        // CORS 预检不带 token
        if ("OPTIONS".equalsIgnoreCase(method)) return false;
        if (matchesAny(PUBLIC_PATHS, path)) return false;
        boolean guestRead = "GET".equalsIgnoreCase(method)
                || ("POST".equalsIgnoreCase(method) && "/dish/search".equals(path));
        return !(guestRead && matchesAny(GUEST_READ_PATHS, path));
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        for (String p : patterns) {
            if (MATCHER.match(p, path)) return true;
        }
        return false;
    }
}
