package com.gudu.xsd.modules.mealplan.domain.model;

import java.util.Set;

/**
 * 单个成员的健康档案（营养报告阈值用）。
 *
 * @param sodiumMaxMg   档案里显式的钠上限（constraints.sodiumMaxMg，可空）
 * @param calorieTarget 按身高体重算出的每日热量目标（可空）
 */
public record MemberHealth(Set<String> audiences, Set<String> allergies, Integer sodiumMaxMg,
                           Integer calorieTarget, Integer proteinTarget, Integer fatTarget) {

    public MemberHealth {
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        allergies = allergies == null ? Set.of() : Set.copyOf(allergies);
    }

    public boolean has(String audience) {
        return audiences.contains(audience);
    }

    /** 该成员单独就餐时的排餐档案（替换建议按此过滤）。 */
    public HouseholdProfile asProfile() {
        int limit = sodiumMaxMg != null ? sodiumMaxMg
                : has(HouseholdProfile.HYPERTENSION) ? HouseholdProfile.HYPERTENSION_SODIUM_LIMIT_MG
                : HouseholdProfile.DEFAULT_SODIUM_LIMIT_MG;
        return new HouseholdProfile(audiences, allergies, limit);
    }
}
