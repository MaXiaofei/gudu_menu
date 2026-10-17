package com.gudu.xsd.modules.mealplan.domain.model;

import java.util.List;

/** 校验结论：passed / 第几轮通过 / 说明（如「钠日均 1.8g（≤2g）」「14 道不重样」）。 */
public record PlanCheck(boolean passed, int rounds, List<String> notes) {

    public PlanCheck {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
