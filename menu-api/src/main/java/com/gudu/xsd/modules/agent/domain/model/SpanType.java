package com.gudu.xsd.modules.agent.domain.model;

/** 调用链 span 类型。 */
public enum SpanType {
    GUARD, LLM, TOOL, RETRIEVAL, ACTION;

    public String code() {
        return name().toLowerCase();
    }
}
