package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.util.Map;

/**
 * 暴露给模型的工具定义（OpenAI function 格式）。
 *
 * @param parameters JSON Schema（object）
 */
public record ToolDef(String name, String description, Map<String, Object> parameters) {

    public ToolDef {
        parameters = parameters == null ? Map.of("type", "object", "properties", Map.of()) : Map.copyOf(parameters);
    }
}
