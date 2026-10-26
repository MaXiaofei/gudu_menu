package com.gudu.xsd.modules.family.interfaces;

import com.gudu.xsd.modules.family.application.FamilyAppService;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 {@link TenantInterceptor}（顺序在 Sa-Token 拦截器之后）。
 * 依赖经 ObjectProvider 取：@WebMvcTest 切片里没有 family 应用服务时不注册（各 Controller 单测自行桩上下文）。
 *
 * <p>{@code gudu.tenant.anonymous-family-id}：未登录请求的兜底家庭，<b>仅当</b> {@code auth.login-check=false}
 * （本地/测试环境）时生效；生产强制登录，此配置无效。
 */
@Configuration
public class TenantWebConfig implements WebMvcConfigurer {

    static final int ORDER_AFTER_SA_TOKEN = 100;

    private final ObjectProvider<FamilyQueryApi> query;
    private final ObjectProvider<FamilyAppService> familyApp;
    private final boolean loginCheck;
    private final Long anonymousFamilyId;

    public TenantWebConfig(ObjectProvider<FamilyQueryApi> query, ObjectProvider<FamilyAppService> familyApp,
                           @Value("${auth.login-check:true}") boolean loginCheck,
                           @Value("${gudu.tenant.anonymous-family-id:}") Long anonymousFamilyId) {
        this.query = query;
        this.familyApp = familyApp;
        this.loginCheck = loginCheck;
        this.anonymousFamilyId = anonymousFamilyId;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        FamilyQueryApi q = query.getIfAvailable();
        FamilyAppService app = familyApp.getIfAvailable();
        if (q == null || app == null) return;
        registry.addInterceptor(new TenantInterceptor(q, app, loginCheck ? null : anonymousFamilyId))
                .addPathPatterns("/**").order(ORDER_AFTER_SA_TOKEN);
    }
}
