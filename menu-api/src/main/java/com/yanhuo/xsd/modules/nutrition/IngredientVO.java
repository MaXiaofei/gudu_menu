package com.yanhuo.xsd.modules.nutrition;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 食材库列表 VO：在 Ingredient 基础上挂 per 100g 营养值。
 * nutrition 的 key 为 metric name（calorie/protein/fat/carb/sugar/gi），value 为 per 100g 含量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IngredientVO extends Ingredient {

    /** metric name -> value(per 100g)。例：{calorie:19, protein:0.9, ...} */
    private Map<String, BigDecimal> nutrition;
}
