package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** plan_week 线上走 mealplan 真实草稿：卡片带 draftId，午晚两餐按天展开、菜去重。 */
class PlanWeekDraftTest {

    @Test
    void 真实草稿转卡片() {
        LocalDate mon = LocalDate.of(2026, 10, 19);
        List<Kitchen.PlanDay> days = List.of(
                new Kitchen.PlanDay(mon, "周一", List.of(
                        new Kitchen.PlanSlot("LUNCH", 1, "香菇鸡肉粥", "c1", List.of("BABY")),
                        new Kitchen.PlanSlot("DINNER", 2, "蒜蓉西兰花", "c2", List.of("EXPIRING")))),
                new Kitchen.PlanDay(mon.plusDays(1), "周二", List.of(
                        new Kitchen.PlanSlot("DINNER", 2, "蒜蓉西兰花", "c2", List.of()))));
        ToolOutcome out = PlanWeekTool.fromDraft(new Kitchen.WeekPlan(42L, mon, mon.plusDays(6), true,
                List.of("钠日均 1.8g（≤2g）"), days));

        assertThat(out.ok()).isTrue();
        Map<String, Object> card = out.cards().get(0);
        assertThat(card).containsEntry("type", "plan").containsEntry("draftId", 42L).containsEntry("range", "10.19–10.25");
        assertThat((List<?>) card.get("items")).hasSize(2);
        assertThat(out.data()).containsEntry("draftId", 42L).containsEntry("passed", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> monday = ((List<Map<String, Object>>) out.data().get("days")).get(0);
        assertThat(monday).containsEntry("lunch", "香菇鸡肉粥").containsEntry("dinner", "蒜蓉西兰花");
    }
}
