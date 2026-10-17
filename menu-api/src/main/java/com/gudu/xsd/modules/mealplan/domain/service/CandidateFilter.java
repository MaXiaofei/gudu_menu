package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;

import java.time.LocalDate;
import java.util.Set;

/**
 * 规则过滤（排餐第一步，LLM/启发式排布只在过滤后的候选里选）：
 * 忌口食材、高血压单菜限钠、高血脂单菜限脂、素食不排荤；工作日时长上限按日判断。
 */
public final class CandidateFilter {

    /** 高血压：1 人份钠 ≤ 900mg（全天 ≤ 2000mg，午晚两餐 + 早餐余量）。 */
    public static final double HYPERTENSION_DISH_SODIUM_MAX = 900;
    /** 高血脂：1 人份脂肪 ≤ 25g。 */
    public static final double HYPERLIPIDEMIA_DISH_FAT_MAX = 25;
    private static final Set<String> HEAVY_TAGS = Set.of("卤味", "烧烤", "重口味");

    private CandidateFilter() {
    }

    public static boolean allowed(DishCandidate c, HouseholdProfile p) {
        for (String allergen : p.allergies()) {
            if (allergen == null || allergen.isBlank()) continue;
            if (c.ingredientNames().stream().anyMatch(n -> n.contains(allergen.trim())) || c.name().contains(allergen.trim())) {
                return false;
            }
        }
        if (p.has(HouseholdProfile.HYPERTENSION) && c.perServing().sodiumMg() > HYPERTENSION_DISH_SODIUM_MAX) return false;
        if (p.has(HouseholdProfile.HYPERLIPIDEMIA)
                && (c.perServing().fatG() > HYPERLIPIDEMIA_DISH_FAT_MAX || c.tags().stream().anyMatch(HEAVY_TAGS::contains))) {
            return false;
        }
        return !(p.has(HouseholdProfile.VEGETARIAN) && c.kind() == DishKind.MEAT);
    }

    public static boolean fitsTime(DishCandidate c, LocalDate date, Integer weekdayMaxMinutes) {
        if (weekdayMaxMinutes == null || !WeekRange.isWeekday(date)) return true;
        return c.minutes() == null || c.minutes() <= weekdayMaxMinutes;
    }
}
