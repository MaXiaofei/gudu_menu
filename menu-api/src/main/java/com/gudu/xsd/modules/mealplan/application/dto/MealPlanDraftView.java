package com.gudu.xsd.modules.mealplan.application.dto;

import java.time.LocalDate;
import java.util.List;

/** 契约 MealPlanDraft（+ status：DRAFT / APPLIED）。 */
public record MealPlanDraftView(Long draftId, LocalDate weekStart, LocalDate weekEnd, List<String> constraints,
                                Check check, List<Day> days, Summary summary, String status) {

    public record Check(boolean passed, int rounds, List<String> notes) {
    }

    public record Day(LocalDate date, String weekday, String note, List<Slot> slots) {
    }

    public record Slot(Long slotId, String meal, DishCardView dish, List<String> badges) {
    }

    /**
     * @param estCostFen   预计采购（分）：需新买食材所在菜的成本估算
     * @param newItems     需要新买的食材样数（余量里没有的，去重）
     * @param usesExpiring 用掉的快到期食材样数（去重）
     */
    public record Summary(int estCostFen, int newItems, int usesExpiring) {
    }
}
