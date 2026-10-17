package com.gudu.xsd.modules.mealplan.domain.model;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** 排餐结果的一格（尚未持久化）。 */
public record PlannedSlot(LocalDate date, Meal meal, long dishId, Set<SlotBadge> badges) {

    public PlannedSlot {
        badges = badges == null || badges.isEmpty() ? EnumSet.noneOf(SlotBadge.class) : EnumSet.copyOf(badges);
    }
}
