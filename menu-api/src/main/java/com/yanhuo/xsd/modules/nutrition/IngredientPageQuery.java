package com.yanhuo.xsd.modules.nutrition;

import com.yanhuo.xsd.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 食材库列表入参（继承分页）。
 * purchaseCategoryId 为空表示全部；非空则按采购分类过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IngredientPageQuery extends PageQuery {

    private Long purchaseCategoryId;
}
