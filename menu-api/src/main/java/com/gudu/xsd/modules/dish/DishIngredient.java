package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    /** 用量数量（对应 unitId 的个数，如 2 表示 2 个/2 把）。 */
    private BigDecimal amount;

    /** 自然单位 → sys_dict(group=unit)。旧数据 = 'g'。 */
    private Long unitId;

    /** 内部记账基准克数 = amount × grams_per_unit（保存时算，查询零换算）。 */
    private BigDecimal grams;

    /** 食材名（非持久化，详情接口批量回填，避免前端 N+1 查名字）。 */
    @TableField(exist = false)
    private String ingredientName;
}
