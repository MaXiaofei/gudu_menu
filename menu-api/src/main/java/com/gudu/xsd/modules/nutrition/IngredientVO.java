package com.gudu.xsd.modules.nutrition;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 食材库列表 VO：在 Ingredient 基础上挂 per 100g 营养值。
 * nutrition 的 key 为 metric name（calorie/protein/fat/carb/sugar/gi），value 为 per 100g 含量。
 *
 * V55（食材去单位）：原 unitName/stockAmount/stockUnitId/stockUnitName/unitGramCount/
 * defaultGramSet（V41/V53 默认单位与换算徽标）随 unit 解绑一并删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IngredientVO extends Ingredient {

    /** metric name -> value(per 100g)。例：{calorie:19, protein:0.9, ...} */
    private Map<String, BigDecimal> nutrition;

    /** 采购品类名（ingredient.purchase_category_id → sys_dict purchase_category）。 */
    private String categoryName;
}
