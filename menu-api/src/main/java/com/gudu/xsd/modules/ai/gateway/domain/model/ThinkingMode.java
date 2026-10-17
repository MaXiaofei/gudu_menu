package com.gudu.xsd.modules.ai.gateway.domain.model;

/**
 * 思考模式（DeepSeek：{@code thinking:{type:disabled|enabled}} + {@code reasoning_effort}）。
 * 开启思考且带工具时，同一轮内 assistant 消息的 reasoning_content 必须原样回传。
 */
public enum ThinkingMode {
    DISABLED, LOW, HIGH, MAX;

    public boolean enabled() {
        return this != DISABLED;
    }

    /** reasoning_effort 取值；关闭时为 null。 */
    public String effort() {
        return enabled() ? name().toLowerCase() : null;
    }

    public static ThinkingMode of(String raw) {
        if (raw == null || raw.isBlank()) return DISABLED;
        return ThinkingMode.valueOf(raw.trim().toUpperCase());
    }
}
