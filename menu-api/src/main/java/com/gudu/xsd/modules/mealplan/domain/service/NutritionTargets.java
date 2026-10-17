package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.MemberHealth;
import com.gudu.xsd.modules.mealplan.domain.model.NutrientKey;

import java.util.EnumMap;
import java.util.Map;

/**
 * 成员每日营养阈值。食记只记录家里做的午晚餐，目标型指标（能量/蛋白/纤维）按全天目标的
 * {@link #HOME_MEAL_SHARE} 折算；上限型（钠/脂肪）按全天上限计，偏严格以便提醒。
 * 参考《中国居民膳食指南》：成人钠 ≤ 2000mg（高血压按档案或 2000mg）、膳食纤维 25g；宝宝辅食按 1–3 岁量。
 */
public final class NutritionTargets {

    public static final double HOME_MEAL_SHARE = 0.65;

    private NutritionTargets() {
    }

    public static Map<NutrientKey, Double> of(MemberHealth h) {
        Map<NutrientKey, Double> t = new EnumMap<>(NutrientKey.class);
        if (h.has(HouseholdProfile.BABY)) {
            t.put(NutrientKey.ENERGY, 900 * HOME_MEAL_SHARE);
            t.put(NutrientKey.PROTEIN, 25 * HOME_MEAL_SHARE);
            t.put(NutrientKey.FAT, 35.0);
            t.put(NutrientKey.SODIUM, h.sodiumMaxMg() != null ? h.sodiumMaxMg() : 700.0);
            t.put(NutrientKey.FIBER, 8 * HOME_MEAL_SHARE);
            return t;
        }
        t.put(NutrientKey.ENERGY, (h.calorieTarget() != null ? h.calorieTarget() : 2000) * HOME_MEAL_SHARE);
        t.put(NutrientKey.PROTEIN, (h.proteinTarget() != null ? h.proteinTarget() : 60) * HOME_MEAL_SHARE);
        double fat = h.fatTarget() != null ? h.fatTarget() : 65;
        t.put(NutrientKey.FAT, h.has(HouseholdProfile.HYPERLIPIDEMIA) ? Math.min(fat, 50) : fat);
        t.put(NutrientKey.SODIUM, (double) h.asProfile().sodiumLimitMg());
        t.put(NutrientKey.FIBER, 25 * HOME_MEAL_SHARE);
        return t;
    }
}
