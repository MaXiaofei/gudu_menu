package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.agent.application.ActionDesk;
import com.gudu.xsd.modules.agent.application.AgentOrchestrator;
import com.gudu.xsd.modules.agent.application.AgentTestKit;
import com.gudu.xsd.modules.agent.application.TurnRequest;
import com.gudu.xsd.modules.agent.application.TurnResult;
import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.agent.application.assess.FixtureKitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 咕嘟对话里的 plan_week 工具生成一周餐程也受每日次数限制：超限时工具返回失败结果，整轮对话不被打断。 */
class PlanWeekQuotaTest {

    @Test
    void 餐程超限_工具失败结果交给模型回复_对话正常结束() {
        AgentTestKit kit = AgentTestKit.create();
        Kitchen limited = new FixtureKitchen(AgentTestKit.fixture(), AgentTestKit.OM) {
            @Override
            public Optional<WeekPlan> draftWeekPlan(LocalDate weekStart, Integer servings, Integer weekdayMaxMinutes) {
                throw BizException.tooManyRequests("今日一周餐程次数已用完，明天再试"); // 与 MealPlanAppService.create 超限一致
            }
        };
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME, "帮我排下周一周的餐程",
                        Map.of(), List.of(), false),
                new AgentOrchestrator.Env(limited, new ActionDesk(kit.store, limited),
                        AssessGateways.simulated().create(null), Map.of()), kit.sink);

        assertThat(r.tools()).extracting(t -> t.name()).contains(PlanWeekTool.NAME);
        var call = r.tools().stream().filter(t -> t.name().equals(PlanWeekTool.NAME)).findFirst().orElseThrow();
        assertThat(call.ok()).isFalse();
        assertThat(call.summary()).contains("今日一周餐程次数已用完");
        assertThat(r.text()).isNotBlank();
        assertThat(kit.sink.events).contains("done").doesNotContain("error");
    }
}
