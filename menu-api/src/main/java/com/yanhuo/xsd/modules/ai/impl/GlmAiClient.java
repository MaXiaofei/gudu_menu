package com.yanhuo.xsd.modules.ai.impl;

import com.yanhuo.xsd.common.BizException;
import com.yanhuo.xsd.modules.ai.AiClient;
import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import com.yanhuo.xsd.modules.ai.dto.MenuRecommendRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GLM AI 客户端：智谱 GLM 接入预留（空壳）。{@code yanhuo.ai.provider=glm} 时启用。
 *
 * <p>接口形态钉死（与 MockAiClient 同签名），后续填真实 GLM 调用不改 AiService/AiController。
 * 当前未接入：所有方法抛 {@link BizException}，提示配置回 mock 或填 GLM key。
 */
@Component
@ConditionalOnProperty(name = "yanhuo.ai.provider", havingValue = "glm")
public class GlmAiClient implements AiClient {

    private static final String NOT_READY = "GLM 未接入，请配置 yanhuo.ai.provider=mock 或填 GLM key";

    @Override
    public NutritionFillResponse fillNutrition(NutritionFillRequest req) {
        throw new BizException(NOT_READY);
    }

    @Override
    public List<MenuCandidate> recommendMenu(MenuRecommendRequest req) {
        throw new BizException(NOT_READY);
    }

    @Override
    public String provider() {
        return "glm";
    }
}
