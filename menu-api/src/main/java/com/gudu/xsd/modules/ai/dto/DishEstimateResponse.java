package com.gudu.xsd.modules.ai.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 菜品/一餐营养估算响应：回显描述 + 估算的各营养指标总量 + 来源 + AI 说明。
 *
 * <p>nutrition：metricId → 该餐估算总量（metricId 1cal/2protein/3fat/4carb/5sugar；
 * gi 不适用整体餐，跳过）。source：deepseek/mock。aiNote：AI 给的估算依据说明。
 *
 * @param description 回显用户输入的描述
 * @param nutrition   metricId → 估算总量（受 servingFactor 缩放后）
 * @param source      deepseek / mock
 * @param aiNote      估算说明（如「估算基于常见份量,仅供参考」）
 */
public record DishEstimateResponse(String description,
                                   Map<Long, BigDecimal> nutrition,
                                   String source,
                                   String aiNote) {
}
