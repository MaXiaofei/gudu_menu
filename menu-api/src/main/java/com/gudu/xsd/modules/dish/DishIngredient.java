package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品-食材用量：某菜用了某食材多少克。
 */
@Data
@TableName("dish_ingredient")
public class DishIngredient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    private Long ingredientId;

    /** 用量克数。 */
    private BigDecimal amount;
}
