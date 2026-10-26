package com.gudu.xsd.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.time.Duration;

/**
 * CORS + 静态资源映射（上传的图片 /uploads/**，映射到 gudu.upload-dir；访问签名校验见 UploadAccessConfig）。
 *
 * <p>允许源由 {@code gudu.cors.allowed-origins}（逗号分隔，支持通配模式）配置：
 * 生产只放行正式域名；本地/测试默认 {@code *}。微信小程序原生请求不带 Origin，不受影响。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${gudu.upload-dir:uploads}")
    private String uploadDir;

    @Value("${gudu.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedHeaders("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .exposedHeaders(TraceIdFilter.HEADER)
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = new File(uploadDir).getAbsolutePath() + File.separator;
        // 访问需签名（UploadAccessConfig）；地址里带过期时间，允许客户端私有缓存 1 小时，禁止共享缓存（CDN/代理）存储
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + location)
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate());
    }
}
