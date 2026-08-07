package com.gudu.xsd.modules.nutrition;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 食材库列表 VO：在 Ingredient 基础上挂 per 100g 营养值。
 * nutrition 的 key 为 metric name（calorie/protein/fat/carb/sugar/gi），value 为 per 100g 含量。
 *
 * V41：另挂当前库存余量（现有 X 个 · 单位，手动添加页「库里已有」/ 食材头展示用），
 * 由 pantry 按食材聚合 SUM(amount) 计算，无库存批次时为 0 + 食材默认单位。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IngredientVO extends Ingredient {

    /** metric name -> value(per 100g)。例：{calorie:19, protein:0.9, ...} */
    private Map<String, BigDecimal> nutrition;

    /** 当前库存余量合计（pantry SUM(amount)，按主单位）。 */
    private BigDecimal stockAmount;

    /** 余量主单位 id（库存批次单位优先，无批次回退食材默认单位）。 */
    private Long stockUnitId;

    /** 余量主单位名（sys_dict group=unit）。 */
    private String stockUnitName;
}
