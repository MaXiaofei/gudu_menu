package com.gudu.xsd.modules.mealplan.domain.repository;

import com.gudu.xsd.modules.mealplan.domain.model.MealPlanDraft;

import java.util.Optional;

public interface MealPlanDraftRepository {

    /** 新建时整体落库（草稿 + 格子并回填 id）；已有则更新草稿与变更的格子。 */
    void save(MealPlanDraft draft);

    Optional<MealPlanDraft> findById(Long id);
}
