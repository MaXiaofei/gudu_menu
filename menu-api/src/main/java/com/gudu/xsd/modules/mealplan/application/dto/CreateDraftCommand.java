package com.gudu.xsd.modules.mealplan.application.dto;

import java.time.LocalDate;
import java.util.List;

/** POST /mealplan/drafts 入参（契约字段；全部可选，缺省：下周一、午晚两餐、全部成员、人份=成员数、优先用快到期）。 */
public record CreateDraftCommand(LocalDate weekStart, List<String> meals, Integer servings, List<Long> memberIds,
                                 Integer weekdayMaxMinutes, Integer budgetFen, Boolean preferExpiring) {
}
