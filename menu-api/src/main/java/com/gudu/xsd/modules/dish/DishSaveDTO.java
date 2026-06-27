package com.gudu.xsd.modules.dish;

import lombok.Data;

import java.util.List;

/**
 * 菜品完整保存入参：基础信息 + 步骤 + 菜系/标签/分类关联 + 食材用量。
 */
@Data
public class DishSaveDTO {

    private Dish dish;

    private List<DishStep> steps;

    private List<Long> cuisineIds;

    private List<Long> tagIds;

    private List<Long> categoryIds;

    /** 食材用量（ingredientId + amount 克）。 */
    private List<DishIngredient> ingredients;
}
