package com.gudu.xsd.modules.nutrition;

import lombok.Data;

import java.util.List;

/**
 * 新增食材入参：食材基础信息 + 营养 EAV 列表。
 */
@Data
public class IngredientSaveDTO {

    private Ingredient ingredient;

    private List<IngredientNutrition> nutritions;
}
