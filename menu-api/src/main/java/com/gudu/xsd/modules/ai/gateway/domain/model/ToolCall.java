package com.gudu.xsd.modules.ai.gateway.domain.model;

/**
 * 模型请求的一次工具调用。
 *
 * @param argumentsJson 参数 JSON 字符串（模型原样输出，由调用方解析与校验）
 */
public record ToolCall(String id, String name, String argumentsJson) {

    public ToolCall {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("工具名不能为空");
        argumentsJson = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
    }
}
