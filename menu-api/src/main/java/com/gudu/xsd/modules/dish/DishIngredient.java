package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品-食材用量：某菜用了某食材多少。
 *
 * V55（食材去单位）：grams 列停用（换算表已删，不再转克），用量表达回到
 * amount + unitName（"2 个"）；采购/备菜聚合按用量原文走，不再按克汇总。
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

    /** 食材名（非持久化，详情接口批量回填，避免前端 N+1 查名字）。 */
    @TableField(exist = false)
    private String ingredientName;

    /** 单位名（非持久化，详情接口按 unitId 批量回填；含「适量/少许/一小把」量词单位，§16.3）。 */
    @TableField(exist = false)
    private String unitName;

    /** 库存档位 ENOUGH/LOW/NONE（非持久化，详情接口批量回填；家里：充足/不足/用完）。B5。 */
    @TableField(exist = false)
    private String stockLevel;
}
