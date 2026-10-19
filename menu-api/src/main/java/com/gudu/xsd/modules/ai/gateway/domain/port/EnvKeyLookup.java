package com.gudu.xsd.modules.ai.gateway.domain.port;

/** 读环境变量来源的 key（如 DEEPSEEK_API_KEY）。 */
public interface EnvKeyLookup {

    /** 变量不存在或为空返回 null。 */
    String get(String envName);
}
