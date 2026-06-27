package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 食材-营养 EAV：某食材某指标 per 100g 的值。
 */
@Data
@TableName("ingredient_nutrition")
public class IngredientNutrition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    private Long metricId;

    private BigDecimal value;
}
