package com.gudu.xsd.modules.mealplan.domain.model;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** 草稿中的一格（slotId 持久化后才有）。 */
public record DraftSlot(Long slotId, LocalDate date, Meal meal, long dishId, Set<SlotBadge> badges) {

    public DraftSlot {
        badges = badges == null || badges.isEmpty() ? EnumSet.noneOf(SlotBadge.class) : EnumSet.copyOf(badges);
    }

    public DraftSlot withDish(long newDishId, Set<SlotBadge> newBadges) {
        return new DraftSlot(slotId, date, meal, newDishId, newBadges);
    }

    public DraftSlot withId(Long id) {
        return new DraftSlot(id, date, meal, dishId, badges);
    }
}
