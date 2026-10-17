package com.gudu.xsd.modules.mealplan.domain.port;

/**
 * LLM 排布端口（集成阶段由模型网关 CHAT 能力实现，基于它的 MealArranger 再接入 MealPlanner；
 * 输出仍须经 PlanValidator 校验回环）。本上下文不直接依赖 modules.ai。
 */
public interface MealPlanModelPort {

    /**
     * @param prompt     排布提示词（含候选菜 id/名称/类别/1 人份钠、约束与快到期食材）
     * @param jsonSchema 期望输出：{"days":[{"date":"2026-10-19","slots":[{"meal":"LUNCH","dishId":1}]}]}
     * @return 模型原始文本
     */
    String arrange(String prompt, String jsonSchema);
}
