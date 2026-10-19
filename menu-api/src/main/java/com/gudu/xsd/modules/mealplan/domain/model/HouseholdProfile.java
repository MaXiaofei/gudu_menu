package com.gudu.xsd.modules.mealplan.domain.model;

import java.util.Set;

/**
 * 就餐成员合并档案（排餐约束取并集）。
 *
 * @param audiences     特殊人群（高血压 / 高血脂 / 宝宝辅食 / 素食 …）
 * @param allergies     忌口食材名（含即排除）
 * @param sodiumLimitMg 每人每日钠上限（mg）
 */
public record HouseholdProfile(Set<String> audiences, Set<String> allergies, int sodiumLimitMg) {

    public static final String HYPERTENSION = "高血压";
    public static final String HYPERLIPIDEMIA = "高血脂";
    public static final String BABY = "宝宝辅食";
    public static final String VEGETARIAN = "素食";
    public static final int DEFAULT_SODIUM_LIMIT_MG = 2300;
    public static final int HYPERTENSION_SODIUM_LIMIT_MG = 2000;

    public HouseholdProfile {
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        allergies = allergies == null ? Set.of() : Set.copyOf(allergies);
    }

    public boolean has(String audience) {
        return audiences.contains(audience);
    }

    public boolean hasBaby() {
        return has(BABY);
    }
}
