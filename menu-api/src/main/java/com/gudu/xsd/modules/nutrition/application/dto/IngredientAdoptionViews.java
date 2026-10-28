package com.gudu.xsd.modules.nutrition.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 运营收编 / 别名维护页的视图对象。 */
public final class IngredientAdoptionViews {

    private IngredientAdoptionViews() {
    }

    /** 各家所选分类的分布（categoryId 可空 = 未选分类）。 */
    public record CategoryShare(Long categoryId, String categoryName, int count) {
    }

    /** 按规整后名称汇总的家庭私有食材。 */
    public record Group(String name, int familyCount, int ingredientCount, LocalDateTime earliest,
                        List<CategoryShare> categories, Long suggestedCategoryId, List<Long> ingredientIds) {
    }

    public record AliasItem(Long id, String alias, Long ingredientId, String ingredientName,
                            LocalDateTime createTime) {
    }

    public record PageResult<T>(List<T> records, long total) {
    }

    /** 收编 / 合并结果。 */
    public record AdoptResult(Long targetId, String targetName, int mergedIngredients, int movedRows,
                              int dedupedRows, List<String> aliasesAdded) {
    }
}
