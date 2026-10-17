package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ingredient")
public class Ingredient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 插画 key（V68，docs/design/assets.json ing）；写入时由 IngredientService 按 IngredientIconPolicy 计算。 */
    private String iconKey;

    /** 关联 sys_dict(purchase_category)。 */
    private Long purchaseCategoryId;

    /** 食用属性：1食用/2饮料零食/3生活用品（V53）。非食用跳过营养计算。 */
    private Integer edible;

    private Integer purchaseCount;

    private Integer usageCount;

    @TableLogic
    private Integer deleted;
}
