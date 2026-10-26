package com.gudu.xsd.modules.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.ai.AiOutputGuard;
import com.gudu.xsd.modules.ai.MenuRecommender;
import com.gudu.xsd.modules.ai.dto.DishEstimateRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.impl.DeepSeekAiClient;
import com.gudu.xsd.modules.ai.impl.MockAiClient;
import com.gudu.xsd.modules.community.application.ai.LlmPostTextWriter;
import com.gudu.xsd.modules.community.domain.service.PostTextWriter;
import com.gudu.xsd.modules.mealplan.application.GatewayMealPlanModelAdapter;
import com.gudu.xsd.modules.mealplan.application.LlmMealArranger;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.Meal;
import com.gudu.xsd.modules.mealplan.domain.model.Nutrients;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;
import com.gudu.xsd.modules.mealplan.domain.service.HeuristicMealArranger;
import com.gudu.xsd.modules.mealplan.domain.service.MealPlanner;
import com.gudu.xsd.modules.vision.application.GatewayVisionModelAdapter;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 除咕嘟助手外的每个模型调用点：最终到达传输层（即发给模型服务商）的消息不含手机号 / openid / wx_ 占位手机号，
 * 也不含成员称呼（这些调用点的提示词本身不带成员信息，这里一并断言）。
 * 咕嘟助手见 {@code AgentPrivacyTest}。
 */
class ModelCallPrivacyTest {

    static final String PHONE = "13812345678";
    static final String OPENID = "oXyZ_abcdefghijklmnopqrstu12";
    static final String WX = "wx_oAbc1234";
    static final List<String> NAMES = List.of("妈妈", "朵朵", "林林");

    final List<ChatRequest> sent = new ArrayList<>();
    final ModelGateway gateway = AssessGateways.simulated(sent::add).create(null);

    void assertClean() {
        assertThat(sent).as("调用到了传输层").isNotEmpty();
        for (ChatRequest r : sent) {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage m : r.messages()) sb.append(m.content()).append('\n');
            String all = sb.toString();
            assertThat(all).doesNotContain(PHONE, OPENID, WX);
            NAMES.forEach(n -> assertThat(all).doesNotContain(n));
        }
    }

    @Test
    void 网关兜底_任意场景出站都隐藏账号标识() {
        gateway.chatText("any", "系统", "手机 " + PHONE + " openid " + OPENID + " 账号 " + WX);
        assertClean();
        assertThat(sent.get(0).messages().get(1).content()).contains("[手机号已隐藏]", "[账号标识已隐藏]");
    }

    @Test
    void 营养补全与菜品估算() {
        DeepSeekAiClient c = new DeepSeekAiClient(new MockAiClient(new MenuRecommender()), new MenuRecommender(),
                new ObjectMapper(), new AiOutputGuard());
        c.setGateway(gateway);
        c.fillNutrition(new NutritionFillRequest("西红柿", 1L));
        c.estimateDish(new DishEstimateRequest("一碗番茄蛋面，备注电话" + PHONE + " " + OPENID, BigDecimal.ONE));
        assertClean();
        assertThat(sent).extracting(ChatRequest::scene).contains("nutrition_fill", "dish_estimate");
    }

    @Test
    void 周餐程_提示词只有聚合档案与候选菜() {
        HouseholdProfile profile = new HouseholdProfile(Set.of("高血压", "宝宝辅食"), Set.of("花生"), 2000);
        List<DishCandidate> pool = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            pool.add(new DishCandidate(i, "菜" + i, 20, Set.of(), Set.of((long) i), List.of(),
                    i % 2 == 0 ? DishKind.MEAT : DishKind.VEG, new Nutrients(300, 15, 10, 400, 2), 800, true));
        }
        PlanRequest req = new PlanRequest(new WeekRange(LocalDate.of(2026, 10, 19)), List.of(Meal.DINNER), 4,
                List.of(2001L, 2002L), null, null, true);
        new MealPlanner(new LlmMealArranger(new GatewayMealPlanModelAdapter(gateway), new HeuristicMealArranger()))
                .plan(req, profile, pool, Set.of());
        assertClean();
        assertThat(sent.get(0).scene()).isEqualTo("mealplan");
        assertThat(sent.get(0).messages().get(1).content()).contains("宝宝").doesNotContain("2001", "2002");
    }

    @Test
    void 拍照识别_只有固定提示词与图片() {
        new GatewayVisionModelAdapter(gateway, mock(ImageStore.class))
                .recognize("https://example.com/uploads/a.jpg", "识别食材", "{}");
        assertClean();
        assertThat(sent.get(0).scene()).isEqualTo("vision");
    }

    @Test
    void 晒菜文案_只有菜名用时难度() {
        new LlmPostTextWriter(gateway).write(new PostTextWriter.Material("番茄炒蛋", 15, "今晚", "简单"), "short");
        assertClean();
    }
}
