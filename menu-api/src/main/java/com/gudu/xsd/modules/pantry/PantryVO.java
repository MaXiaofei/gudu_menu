package com.gudu.xsd.modules.pantry;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 食材库存列表 VO：在 Pantry 基础上挂食材名（前端展示用，pantry 只存 ingredient_id）。
 * 参照 IngredientVO 范式：extends 实体 + 附加展示字段。
 *
 * V55（食材去单位）：unitName 字段随单位解绑删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PantryVO extends Pantry {

    /** 食材名（join ingredient）。 */
    private String ingredientName;
}
