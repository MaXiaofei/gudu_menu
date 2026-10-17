package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.gudu.xsd.modules.ai.gateway.domain.port.EnvKeyLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 环境变量来源的 key：先读同名环境变量 / 系统属性；兼容旧配置项（gudu.ai.deepseek.key / gudu.ai.glm.key）。
 */
@Component
@RequiredArgsConstructor
public class SpringEnvKeyLookup implements EnvKeyLookup {

    private static final Map<String, String> LEGACY = Map.of(
            "DEEPSEEK_API_KEY", "gudu.ai.deepseek.key",
            "GLM_API_KEY", "gudu.ai.glm.key");

    private final Environment env;

    @Override
    public String get(String envName) {
        if (envName == null || envName.isBlank()) return null;
        String v = env.getProperty(envName);
        if ((v == null || v.isBlank()) && LEGACY.containsKey(envName)) {
            v = env.getProperty(LEGACY.get(envName));
        }
        return v == null || v.isBlank() ? null : v.trim();
    }
}
