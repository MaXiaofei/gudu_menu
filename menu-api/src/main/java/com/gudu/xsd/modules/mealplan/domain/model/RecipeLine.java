package com.gudu.xsd.modules.mealplan.domain.model;

import java.math.BigDecimal;

/**
 * 估算用的用料行：食材名、采购品类名、用量 + 单位名，以及食材库里的每 100g 能量/蛋白/脂肪（缺则 null）。
 */
public record RecipeLine(String name, String categoryName, BigDecimal amount, String unitName,
                         Double energyPer100g, Double proteinPer100g, Double fatPer100g) {
}
