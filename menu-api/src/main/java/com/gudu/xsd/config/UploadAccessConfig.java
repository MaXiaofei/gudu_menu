package com.gudu.xsd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.upload.UploadUrlJacksonModule;
import com.gudu.xsd.common.upload.UploadUrlSigner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * 上传文件访问控制（A.3）：短时签名 URL。
 *
 * <ul>
 *   <li>{@code /uploads/**} 必须带有效 {@code ?e=&s=} 签名，否则 403（小程序 &lt;image&gt; 与微信拉图都无法带登录头，故用签名而非登录态）；</li>
 *   <li>HTTP 出参统一签名、入参验签剥签名：在 Jackson 消息转换器上挂 {@link UploadUrlJacksonModule}（copy 一份 ObjectMapper，
 *       不影响其它用途的 ObjectMapper）；</li>
 *   <li>{@code gudu.upload-sign.enabled=false}（dev/test）只关校验；配了 secret 仍会签名。</li>
 * </ul>
 */
@Configuration
public class UploadAccessConfig implements WebMvcConfigurer {

    private final UploadUrlSigner signer;

    public UploadAccessConfig(@Value("${gudu.upload-sign.secret:}") String secret,
                              @Value("${gudu.upload-sign.enabled:false}") boolean enabled,
                              @Value("${gudu.upload-sign.ttl:24h}") Duration ttl,
                              @Value("${gudu.upload-sign.input-grace:7d}") Duration inputGrace,
                              ObjectProvider<Clock> clock) {
        this.signer = new UploadUrlSigner(secret, ttl, inputGrace, enabled, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    public UploadUrlSigner uploadUrlSigner() {
        return signer;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UploadSignatureInterceptor(signer)).addPathPatterns("/uploads/**");
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        if (!signer.signing() && !signer.enforcing()) return;
        // 换成新实例而非改原实例：Boot 的 HttpMessageConverters bean 与 RestTemplate/RestClient 共享同一批转换器，
        // 调外部服务（微信、模型）的请求体不能被签名/剥签名
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i).getClass() == MappingJackson2HttpMessageConverter.class) {
                ObjectMapper copy = ((MappingJackson2HttpMessageConverter) converters.get(i)).getObjectMapper().copy();
                copy.registerModule(new UploadUrlJacksonModule(signer));
                converters.set(i, new MappingJackson2HttpMessageConverter(copy));
            }
        }
    }

    /** 校验 /uploads/** 的签名参数。 */
    static final class UploadSignatureInterceptor implements HandlerInterceptor {
        private static final String PREFIX = "/uploads/";
        private static final UrlPathHelper PATHS = new UrlPathHelper();

        private final UploadUrlSigner signer;

        UploadSignatureInterceptor(UploadUrlSigner signer) {
            this.signer = signer;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!signer.enforcing() || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            String path = PATHS.getPathWithinApplication(request);
            String rel = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : null;
            if (signer.verifyRequest(rel, request.getParameter(UploadUrlSigner.PARAM_EXPIRES),
                    request.getParameter(UploadUrlSigner.PARAM_SIGNATURE))) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
    }
}
