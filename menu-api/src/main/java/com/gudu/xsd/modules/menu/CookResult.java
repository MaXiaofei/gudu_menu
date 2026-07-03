package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.pantry.PantryService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 做菜扣库存结果。
 *
 * @param menuId           整集做=食集 id；单菜直做=null
 * @param deductions       各食材的扣减明细（含实扣/欠量/批次）
 * @param shortages        欠量明细 ingredientId → 欠多少克（家里没有或不够的）
 * @param cookingRecordIds 写入的 cooking_record id 列表（整集做=每菜一条；单菜=一条）
 */
public record CookResult(Long menuId,
                         List<PantryService.DeductResult> deductions,
                         Map<Long, BigDecimal> shortages,
                         List<Long> cookingRecordIds) {}
