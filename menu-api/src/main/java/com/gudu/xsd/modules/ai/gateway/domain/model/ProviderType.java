package com.gudu.xsd.modules.ai.gateway.domain.model;

/** 供应商协议类型。 */
public enum ProviderType {
    /** OpenAI 兼容协议（DeepSeek / 智谱 GLM / 其他）。 */
    OPENAI_COMPATIBLE,
    /** 规则兜底（不发网络请求）。 */
    MOCK
}
