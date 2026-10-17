package com.gudu.xsd.modules.ai.gateway.domain.model;

/** 模型能力：网关按能力路由。 */
public enum Capability {
    /** 纯文本对话 / JSON 输出（营养补全、文案、餐程排布）。 */
    CHAT,
    /** 带工具调用（Function Calling）的对话（咕嘟 Agent）。 */
    TOOLS,
    /** 多模态：图片 + 文本（拍照识别）。 */
    VISION;

    public static Capability of(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("能力不能为空");
        return Capability.valueOf(raw.trim().toUpperCase());
    }
}
