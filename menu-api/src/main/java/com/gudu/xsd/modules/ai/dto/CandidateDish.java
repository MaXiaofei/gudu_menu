package com.gudu.xsd.modules.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 候选菜（AiService 已查好的组装结构，随 {@link MenuRecommendRequest} 一并传给 AiClient）。
 *
 * <p> nutrition 为 per 份营养（已按份数折算，非 per100g），metricId -> 值。
 * ingredientNames 为该菜食材名列表（用于 LLM 理解 + 过敏识别）。
 *
 * @param dishId         dishId
 * @param name           菜名
 * @param price          单价（1 份）
 * @param nutrition      per 份营养 metricId -> 值
 * @param ingredientNames 食材名列表
 */
public record CandidateDish(
        Long dishId,
        String name,
        BigDecimal price,
        Map<Long, BigDecimal> nutrition,
        List<String> ingredientNames) {
}
