package com.gudu.xsd.modules.mealplan.domain.service;

import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;

import java.util.List;

/** 排布策略：在过滤后的候选里给 ctx.days × meals 每格选一道菜（启发式实现 / LLM 实现）。 */
public interface MealArranger {

    List<PlannedSlot> arrange(ArrangeContext ctx);
}
