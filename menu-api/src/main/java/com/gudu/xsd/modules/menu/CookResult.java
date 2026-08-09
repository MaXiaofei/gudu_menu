package com.gudu.xsd.modules.menu;

import java.util.List;

/**
 * 做菜确认结果（POST /menu/{id}/cook，V42）。
 * 档位更新在 pantry 侧完成；这里只回传食集 id 与写入的食记记录 id。
 */
public record CookResult(Long menuId, List<Long> cookingRecordIds) {
}
