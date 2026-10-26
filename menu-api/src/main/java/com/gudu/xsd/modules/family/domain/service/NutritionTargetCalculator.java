package com.gudu.xsd.modules.family.domain.service;

import com.gudu.xsd.modules.family.domain.model.HealthProfile;
import com.gudu.xsd.modules.family.domain.model.NutritionTarget;

import java.util.Locale;
import java.util.Optional;

/**
 * 每日营养目标计算（纯函数，原 MemberController 内联逻辑下沉，数值行为不变）：
 * Mifflin-St Jeor BMR × 活动系数 + 体控目标调整；蛋白按体重系数，脂肪 25% 热量，碳水取剩余。
 * 身高 / 体重 / 年龄任一缺失或非正 → empty（前端据此不开精准模式）。
 */
public final class NutritionTargetCalculator {

    private static final double MALE_BMR_CONST = 5;
    private static final double FEMALE_BMR_CONST = -161;

    private NutritionTargetCalculator() {
    }

    public static Optional<NutritionTarget> calculate(HealthProfile hp, String goal, String activityLevel) {
        if (hp == null || !hp.hasBodyMetrics()) return Optional.empty();
        double weight = hp.weightKg();
        double bmr = 10 * weight + 6.25 * hp.heightCm() - 5 * hp.age();
        bmr += "female".equalsIgnoreCase(hp.gender()) ? FEMALE_BMR_CONST : MALE_BMR_CONST;
        double tdee = bmr * activityMultiplier(activityLevel);
        int calorie = (int) Math.round(tdee + goalAdjust(goal));
        double proteinPerKg = "GAIN".equalsIgnoreCase(goal) || "LOSE".equalsIgnoreCase(goal) ? 2.0 : 1.6;
        int protein = (int) Math.round(weight * proteinPerKg);
        int fat = (int) Math.round(calorie * 0.25 / 9);
        int carb = Math.max(0, (int) Math.round((calorie - protein * 4 - fat * 9) / 4.0));
        return Optional.of(new NutritionTarget(calorie, protein, carb, fat, goal, (int) Math.round(bmr)));
    }

    static double activityMultiplier(String level) {
        if (level == null) return 1.2;
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "LIGHT" -> 1.375;
            case "MODERATE" -> 1.55;
            case "ACTIVE" -> 1.725;
            default -> 1.2;
        };
    }

    static double goalAdjust(String goal) {
        if (goal == null) return 0;
        return switch (goal.toUpperCase(Locale.ROOT)) {
            case "LOSE" -> -500;
            case "GAIN" -> 300;
            default -> 0;
        };
    }
}
