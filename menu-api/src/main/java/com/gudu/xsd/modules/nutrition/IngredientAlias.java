package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 食材别名（V95，全局表）：别名 -> 公共食材；alias 全局唯一，只指向公共食材。 */
@Data
@TableName("ingredient_alias")
public class IngredientAlias {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 别名（已按 IngredientNameNormalizer 规整）。 */
    private String alias;

    private Long ingredientId;

    private LocalDateTime createTime;
}
