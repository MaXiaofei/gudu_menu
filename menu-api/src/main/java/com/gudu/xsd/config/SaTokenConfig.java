package com.gudu.xsd.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器：默认全部拦截并强制登录，放行登录与接口文档相关路径。
 *
 * <p>auth.login-check=false 时不做 token 校验（登录态仍由各接口按需获取），
 * 用于小程序审核期/调试期放开访问。默认 false = 不拦截。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Value("${auth.login-check:false}")
    private boolean loginCheck;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // CORS 预检（OPTIONS）不带 token，直接放行——否则 H5 跨域 preflight 500 被浏览器判 CORS 失败
            if ("OPTIONS".equalsIgnoreCase(cn.dev33.satoken.context.SaHolder.getRequest().getMethod())) {
                return;
            }
            if (loginCheck) {
                StpUtil.checkLogin();
            }
        }))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/wx-login", // V50 微信小程序静默登录（免登录调用）
                        "/auth/wx-echo",  // 微信公众平台「服务器配置」地址验证（GET echostr 应答）
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/uploads/**",
                        // V45 聚餐：邀请链接免登录（H5 访客入口/加入），聚餐清单与加菜走身份头（member 或 X-Guest-Key）
                        "/invite/**",
                        "/menu/*/together",
                        "/menu/*/together/**",
                        // 聚餐 H5 朋友端点菜页（classpath:/static/together.html）
                        "/together.html"
                );
    }
}
