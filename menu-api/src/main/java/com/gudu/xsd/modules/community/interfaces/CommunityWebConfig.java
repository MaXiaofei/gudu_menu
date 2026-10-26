package com.gudu.xsd.modules.community.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 厨房圈 Web 配置：注册小程序写操作拦截（A.1）。在 Sa-Token 登录拦截之后执行。 */
@Configuration
@RequiredArgsConstructor
public class CommunityWebConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MiniProgramCommunityGuard(objectMapper))
                .addPathPatterns("/community", "/community/**");
    }
}
