package com.gudu.xsd.modules.ai.dto;

import com.gudu.xsd.modules.nutrition.IngredientNutrition;

import java.util.List;

/**
 * 营养补全响应：per 100g 的各指标值（metricId 1cal/2protein/3fat/4carb/5sugar/6gi）+ 来源(mock/glm)。
 */
public record NutritionFillResponse(List<IngredientNutrition> nutrition, String source) {
}
