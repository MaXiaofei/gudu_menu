package com.gudu.xsd.modules.ai.dto;

/**
 * 营养补全请求：用户输入食材名（可选关联已有 ingredientId，非空则补全后落库到该食材）。
 */
public record NutritionFillRequest(String name, Long ingredientId) {
}
