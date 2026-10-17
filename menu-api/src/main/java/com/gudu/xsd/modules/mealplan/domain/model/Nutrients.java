package com.gudu.xsd.modules.mealplan.domain.model;

/** 五项营养值（能量 kcal、蛋白 g、脂肪 g、钠 mg、膳食纤维 g）。不可变。 */
public record Nutrients(double energyKcal, double proteinG, double fatG, double sodiumMg, double fiberG) {

    public static final Nutrients ZERO = new Nutrients(0, 0, 0, 0, 0);

    public Nutrients plus(Nutrients o) {
        return new Nutrients(energyKcal + o.energyKcal, proteinG + o.proteinG, fatG + o.fatG,
                sodiumMg + o.sodiumMg, fiberG + o.fiberG);
    }

    public Nutrients scale(double f) {
        return new Nutrients(energyKcal * f, proteinG * f, fatG * f, sodiumMg * f, fiberG * f);
    }

    public double get(NutrientKey key) {
        return switch (key) {
            case ENERGY -> energyKcal;
            case PROTEIN -> proteinG;
            case FAT -> fatG;
            case SODIUM -> sodiumMg;
            case FIBER -> fiberG;
        };
    }
}
