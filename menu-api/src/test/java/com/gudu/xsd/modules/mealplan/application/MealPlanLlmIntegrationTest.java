package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.application.ModelUnavailableException;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.Meal;
import com.gudu.xsd.modules.mealplan.domain.model.Nutrients;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.SlotBadge;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;
import com.gudu.xsd.modules.mealplan.domain.port.MealPlanModelPort;
import com.gudu.xsd.modules.mealplan.domain.service.HeuristicMealArranger;
import com.gudu.xsd.modules.mealplan.domain.service.MealPlanner;
import com.gudu.xsd.modules.mealplan.domain.service.PlanValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 排餐接入模型网关：LLM 排布结果的过滤与补位、失败退回启发式、网关请求参数、「每餐一道正菜」校验。 */
class MealPlanLlmIntegrationTest {

    static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    static final HouseholdProfile FAMILY = new HouseholdProfile(Set.of(), Set.of(), 2300);
    static final HouseholdProfile WITH_BABY = new HouseholdProfile(Set.of("宝宝辅食"), Set.of(), 2300);

    static DishCandidate cand(long id, DishKind kind, double sodium, boolean baby) {
        return new DishCandidate(id, "菜品" + (char) ('甲' + id), 20, Set.of(), Set.of(id), List.of(), kind,
                new Nutrients(300, 15, 10, sodium, 2), 800, baby);
    }

    static List<DishCandidate> pool() {
        List<DishCandidate> pool = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            pool.add(cand(i, i % 3 == 0 ? DishKind.SOUP : (i % 2 == 0 ? DishKind.MEAT : DishKind.VEG), 400, i % 2 == 1));
        }
        pool.add(cand(90, DishKind.STAPLE, 50, true));   // 小米粥一类：不能单独成餐
        return pool;
    }

    static PlanRequest dinnerOnly() {
        return new PlanRequest(new WeekRange(MONDAY), List.of(Meal.DINNER), 3, List.of(), null, null, true);
    }

    /** 模型照单全收：7 天晚饭依次用 1..7 号菜。 */
    static String fullWeekJson(long firstId) {
        StringBuilder sb = new StringBuilder("```json\n{\"days\":[");
        for (int i = 0; i < 7; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"date\":\"").append(MONDAY.plusDays(i)).append("\",\"slots\":[{\"meal\":\"DINNER\",\"dishId\":")
                    .append(firstId + i).append("}]}");
        }
        return sb.append("]}\n```").toString();
    }

    @Test
    void LLM_排布被采纳_并补角标() {
        MealPlanModelPort port = (prompt, schema) -> fullWeekJson(1);
        MealPlanner.Outcome out = new MealPlanner(new LlmMealArranger(port, new HeuristicMealArranger()))
                .plan(dinnerOnly(), FAMILY, pool(), Set.of(3L));
        assertThat(out.slots()).extracting(PlannedSlot::dishId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(out.slots().get(2).badges()).contains(SlotBadge.EXPIRING);
        assertThat(out.check().passed()).isTrue();
    }

    @Test
    void 非法格子被丢弃_整天交给启发式补() {
        // 周二给了候选外的菜，周三重复周一的菜：这两天由启发式补；其余天保留模型结果
        String json = fullWeekJson(1).replace("\"dishId\":2}", "\"dishId\":999}").replace("\"dishId\":3}", "\"dishId\":1}");
        MealPlanModelPort port = (prompt, schema) -> json;
        MealPlanner.Outcome out = new MealPlanner(new LlmMealArranger(port, new HeuristicMealArranger()))
                .plan(dinnerOnly(), FAMILY, pool(), Set.of());
        assertThat(out.slots()).hasSize(7);
        assertThat(out.slots().stream().map(PlannedSlot::dishId).distinct()).hasSize(7);
        assertThat(out.slots()).noneMatch(s -> s.dishId() == 999L);
        assertThat(out.slots().get(0).dishId()).isEqualTo(1L);
        assertThat(out.slots().get(3).dishId()).isEqualTo(4L);
    }

    @Test
    void 模型失败或输出不是JSON_退回启发式() {
        List<DishCandidate> pool = pool();
        PlanRequest req = dinnerOnly();
        List<PlannedSlot> heuristic = new MealPlanner(new HeuristicMealArranger()).plan(req, FAMILY, pool, Set.of()).slots();
        MealPlanModelPort boom = (p, s) -> {
            throw new ModelUnavailableException("模型暂不可用");
        };
        MealPlanModelPort prose = (p, s) -> "抱歉，我无法排餐";
        assertThat(new MealPlanner(new LlmMealArranger(boom, new HeuristicMealArranger()))
                .plan(req, FAMILY, pool, Set.of()).slots()).isEqualTo(heuristic);
        assertThat(new MealPlanner(new LlmMealArranger(prose, new HeuristicMealArranger()))
                .plan(req, FAMILY, pool, Set.of()).slots()).isEqualTo(heuristic);
    }

    @Test
    void 提示词带约束与候选_不含甜品() {
        AtomicReference<String> seen = new AtomicReference<>();
        List<DishCandidate> pool = new ArrayList<>(pool());
        pool.add(cand(91, DishKind.DESSERT, 10, true));
        MealPlanModelPort port = (prompt, schema) -> {
            seen.compareAndSet(null, prompt);
            return fullWeekJson(1);
        };
        new MealPlanner(new LlmMealArranger(port, new HeuristicMealArranger())).plan(dinnerOnly(), WITH_BABY, pool, Set.of());
        assertThat(seen.get()).contains("2026-10-19(周一)").contains("baby=1").contains("不要只排粥")
                .contains("|主食|").doesNotContain("|甜品|");
    }

    @Test
    void 每餐一道正菜_主食单独成餐算问题() {
        List<DishCandidate> pool = pool();
        Map<Long, DishCandidate> byId = pool.stream().collect(Collectors.toMap(DishCandidate::dishId, Function.identity()));
        List<PlannedSlot> slots = List.of(new PlannedSlot(MONDAY, Meal.DINNER, 90L, Set.of(SlotBadge.BABY)));
        List<PlanValidator.DayIssue> issues = PlanValidator.validate(slots, byId, FAMILY, List.of(MONDAY), 1);
        assertThat(issues).anyMatch(i -> i.reason().contains("缺一道正菜") && i.offendingDishIds().contains(90L));
        // 启发式默认也不会把主食排成一餐
        List<PlannedSlot> planned = new MealPlanner(new HeuristicMealArranger()).plan(dinnerOnly(), FAMILY, pool, Set.of()).slots();
        assertThat(planned).noneMatch(s -> s.dishId() == 90L);
    }

    @Test
    void 网关适配器_CHAT_JSON_思考low() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.chat(any())).thenReturn(new ChatResult(ModelReply.text("{\"days\":[]}"), "deepseek",
                "deepseek-flash", false, 10, BigDecimal.ZERO, List.of()));
        String out = new GatewayMealPlanModelAdapter(gateway).arrange("排一周", LlmMealArranger.SCHEMA);
        assertThat(out).isEqualTo("{\"days\":[]}");
        ArgumentCaptor<ChatRequest> req = ArgumentCaptor.forClass(ChatRequest.class);
        verify(gateway).chat(req.capture());
        assertThat(req.getValue().capability()).isEqualTo(Capability.CHAT);
        assertThat(req.getValue().jsonMode()).isTrue();
        assertThat(req.getValue().thinking()).isEqualTo(ThinkingMode.LOW);
        assertThat(req.getValue().scene()).isEqualTo("mealplan");
    }
}
