package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 一周餐程紧急关停：对话工具 plan_week 关停时不生成（不调大模型），返回失败结果让模型如实告知用户。 */
class PlanWeekSwitchTest {

    @Test
    void 一周餐程关停_不生成_返回暂停提示() {
        Kitchen k = mock(Kitchen.class);
        when(k.mealPlanEnabled()).thenReturn(false);
        when(k.now()).thenReturn(LocalDateTime.of(2026, 10, 26, 19, 0));

        ToolOutcome out = new PlanWeekTool().run(new ToolContext(k, 1L, "s", "排一周", null), Map.of());

        assertThat(out.ok()).isFalse();
        assertThat(out.summary()).isEqualTo("一周餐程暂停服务，其他功能不受影响");
        assertThat(out.data()).containsEntry("error", "一周餐程暂停服务，其他功能不受影响");
        verify(k, never()).draftWeekPlan(any(), any(), any());
        verify(k, never()).searchDishes(any()); // 连轻量草稿也不排
    }

    @Test
    void 一周餐程开放_照常走生成() {
        Kitchen k = mock(Kitchen.class);
        when(k.mealPlanEnabled()).thenReturn(true);
        when(k.now()).thenReturn(LocalDateTime.of(2026, 10, 26, 19, 0));
        when(k.draftWeekPlan(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(k.members()).thenReturn(java.util.List.of());
        when(k.searchDishes(any())).thenReturn(java.util.List.of());

        new PlanWeekTool().run(new ToolContext(k, 1L, "s", "排一周", null), Map.of());

        verify(k).draftWeekPlan(any(), any(), any());
    }
}
