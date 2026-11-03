package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.mealplan.domain.port.MealPlanModelPort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 排餐端口 → 模型网关 CHAT（JSON 输出，关闭思考：输出只是十几格 JSON，思考会烧掉数千 token、拖慢到十几秒）。
 * 网关全部失败时抛 {@code ModelUnavailableException}，由 {@link LlmMealArranger} 退回启发式。
 */
@Component
public class GatewayMealPlanModelAdapter implements MealPlanModelPort {

    static final String SCENE = "mealplan";
    static final String SYSTEM = "你是家庭营养师，按用户给出的候选和规则排一周餐程。只输出 JSON，不要解释。";

    private final ModelGateway gateway;

    public GatewayMealPlanModelAdapter(ModelGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String arrange(String prompt, String jsonSchema) {
        return arrange(prompt, jsonSchema, null);
    }

    @Override
    public String arrange(String prompt, String jsonSchema, Long operatorId) {
        ChatRequest req = new ChatRequest(Capability.CHAT, SCENE,
                List.of(ChatMessage.system(SYSTEM + "\n输出格式：" + jsonSchema), ChatMessage.user(prompt)),
                List.of(), true, null, operatorId, null, false, ThinkingMode.DISABLED);
        return gateway.chat(req).content();
    }
}
