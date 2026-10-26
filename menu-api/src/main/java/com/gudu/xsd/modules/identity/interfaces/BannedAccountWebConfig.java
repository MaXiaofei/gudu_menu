package com.gudu.xsd.modules.identity.interfaces;

import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 {@link BannedAccountInterceptor}（Sa-Token 之后、租户拦截器之前）。
 * @WebMvcTest 切片里没有封禁服务时不注册。
 */
@Configuration
public class BannedAccountWebConfig implements WebMvcConfigurer {

    static final int ORDER = 50;

    private final ObjectProvider<AccountBanAppService> bans;

    public BannedAccountWebConfig(ObjectProvider<AccountBanAppService> bans) {
        this.bans = bans;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        AccountBanAppService b = bans.getIfAvailable();
        if (b == null) return;
        registry.addInterceptor(new BannedAccountInterceptor(b)).addPathPatterns("/**").order(ORDER);
    }
}
