package com.gudu.xsd.modules.nutrition.domain.repository;

import java.util.Map;

/** 食材使用频次读模型（领域端口）。 */
public interface IngredientUsageStats {

    /** 该成员做过的菜（cooking_record）里各食材出现次数。 */
    Map<Long, Integer> cookedUsageByMember(Long memberId);

    /** 菜谱库（未删除菜谱）里各食材被多少道菜用到。 */
    Map<Long, Integer> recipeUsage();
}
