package com.gudu.xsd.modules.nutrition;

import com.gudu.xsd.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 食材库列表入参（继承分页）。
 * purchaseCategoryId 为空表示全部；非空则按采购分类过滤。
 * keyword 为空表示全部；非空则按 name LIKE 模糊搜索（双筛选）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IngredientPageQuery extends PageQuery {

    private Long purchaseCategoryId;

    /** 名称模糊搜索关键词（name LIKE %keyword%）。 */
    private String keyword;
}
