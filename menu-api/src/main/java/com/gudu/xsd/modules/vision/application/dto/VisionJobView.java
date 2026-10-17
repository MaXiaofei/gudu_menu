package com.gudu.xsd.modules.vision.application.dto;

import com.gudu.xsd.modules.vision.domain.model.RecognizedItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 识别任务出参（契约 VisionJob）。recipe 仅 RECIPE 模式。
 * degraded = 视觉模型不可用时用了后备模型或示例结果，客户端提示「请核对」。
 */
public record VisionJobView(Long jobId, String mode, String status, String error, LocalDateTime imageExpiresAt,
                            List<RecognizedItem> items, RecipeView recipe, boolean degraded) {

    public record RecipeView(String title, List<Line> ingredients, List<String> steps) {
    }

    /** 契约 IngredientLine。 */
    public record Line(Long ingredientId, String name, String iconKey, String amount, String unit) {
    }

    /** amount 数字字符串化（去尾零），供 Line 使用。 */
    public static String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }
}
