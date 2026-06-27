package com.gudu.xsd.modules.ai.dto;

import java.math.BigDecimal;

/**
 * 菜品/一餐营养估算请求（V2 方案2：纯文字描述 → AI 估总量）。
 *
 * <p>与 {@link NutritionFillRequest} 区别：营养补全是按食材名返回 per100g；
 * 本请求是用户对「一道菜/一餐」的整体描述（如「一盘番茄炒蛋,2个鸡蛋2个番茄」），
 * AI 估算该餐的总量（不是 per100g）。
 *
 * @param description   用户对菜品/一餐的文字描述
 * @param servingFactor 份数（默认 1，可空；如 0.5 表示半份，2 表示双份）
 */
public record DishEstimateRequest(String description, BigDecimal servingFactor) {
}
