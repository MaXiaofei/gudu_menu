package com.gudu.xsd.modules.pantry.domain.model;

/** 一道菜的一项用料（覆盖率计算输入，值对象）。condiment=调料，不计入覆盖率。 */
public record DishMaterial(Long ingredientId, String name, boolean condiment) {
}
