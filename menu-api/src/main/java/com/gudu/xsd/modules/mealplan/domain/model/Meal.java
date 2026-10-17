package com.gudu.xsd.modules.mealplan.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 餐次（餐程只排午晚两餐）。 */
public enum Meal {
    LUNCH("午餐"), DINNER("晚餐");

    private final String label;

    Meal(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Meal of(String value) {
        if (value != null) {
            for (Meal m : values()) {
                if (m.name().equalsIgnoreCase(value.trim())) return m;
            }
        }
        throw new DomainException("餐次不合法（LUNCH / DINNER）");
    }
}
