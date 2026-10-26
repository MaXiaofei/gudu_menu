package com.gudu.xsd.modules.family.domain.model;

/** 每日营养目标（kcal / g）。 */
public record NutritionTarget(int calorieTarget, int proteinTarget, int carbTarget, int fatTarget, String goal,
                              int bmr) {
}
