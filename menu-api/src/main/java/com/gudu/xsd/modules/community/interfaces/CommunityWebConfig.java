package com.gudu.xsd.modules.community.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.community.application.CommunitySwitchAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 厨房圈 Web 配置（在 Sa-Token 登录拦截之后执行，只挂用户侧 /community/**，管理后台 /admin/community/** 不受影响）：
 * <ol>
 *   <li>A.12 紧急关停：开关关闭时读写统一「厨房圈暂停服务」；</li>
 *   <li>A.1 小程序来源的写操作 403。</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class CommunityWebConfig implements WebMvcConfigurer {

    static final String[] USER_PATHS = {"/community", "/community/**"};

    private final ObjectMapper objectMapper;
    /** ObjectProvider：@WebMvcTest 切片里没有应用服务时不拦截。 */
    private final ObjectProvider<CommunitySwitchAppService> switches;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CommunitySuspendGuard(() -> {
            CommunitySwitchAppService s = switches.getIfAvailable();
            return s == null || s.isEnabled();
        }, objectMapper)).addPathPatterns(USER_PATHS);
        registry.addInterceptor(new MiniProgramCommunityGuard(objectMapper)).addPathPatterns(USER_PATHS);
    }
}
