package com.gudu.xsd.modules.ai.gateway.application;

import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.KeySource;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelPrice;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelSpec;
import com.gudu.xsd.modules.ai.gateway.domain.model.PeakHours;
import com.gudu.xsd.modules.ai.gateway.domain.model.ProviderType;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

/**
 * 内置默认配置（与 V73 种子一致）：库表未迁移或读取失败时网关仍可工作。
 * DeepSeek 两个模型（deepseek-flash 多模态 / deepseek-v4-pro 纯文本），GLM 作后备（默认停用），Mock 规则兜底。
 */
public final class GatewayDefaults {

    public static final long DEEPSEEK_ID = 1L;
    public static final long GLM_ID = 2L;
    public static final long MOCK_ID = 3L;

    public static final String DEEPSEEK_FLASH = "deepseek-flash";
    public static final String DEEPSEEK_PRO = "deepseek-v4-pro";
    public static final String GLM_PLUS = "glm-4-plus";
    public static final String GLM_VISION = "glm-4v-plus";
    public static final String MOCK_MODEL = "mock";

    public static final int DEFAULT_TIMEOUT_MS = 30_000;
    public static final int VISION_TIMEOUT_MS = 60_000;

    private GatewayDefaults() {
    }

    public static List<ModelProvider> providers() {
        ModelProvider deepSeek = ModelProvider.restore(DEEPSEEK_ID, "DeepSeek", ProviderType.OPENAI_COMPATIBLE,
                "https://api.deepseek.com/v1", null, KeySource.ENV, "DEEPSEEK_API_KEY", true, List.of(
                        new ModelSpec(DEEPSEEK_FLASH, EnumSet.of(Capability.CHAT, Capability.TOOLS, Capability.VISION),
                                price("4", "200", "800"), PeakHours.deepSeek(), true),
                        new ModelSpec(DEEPSEEK_PRO, EnumSet.of(Capability.CHAT, Capability.TOOLS),
                                price("30", "900", "2700"), PeakHours.deepSeek(), true)), null);
        ModelProvider glm = ModelProvider.restore(GLM_ID, "GLM", ProviderType.OPENAI_COMPATIBLE,
                "https://open.bigmodel.cn/api/paas/v4", null, KeySource.ENV, "GLM_API_KEY", false, List.of(
                        new ModelSpec(GLM_PLUS, EnumSet.of(Capability.CHAT, Capability.TOOLS),
                                price("500", "500", "500"), null, false),
                        new ModelSpec(GLM_VISION, EnumSet.of(Capability.VISION),
                                price("400", "400", "400"), null, false)), null);
        ModelProvider mock = ModelProvider.restore(MOCK_ID, "Mock", ProviderType.MOCK, null, null, KeySource.NONE,
                null, true, List.of(new ModelSpec(MOCK_MODEL,
                        EnumSet.allOf(Capability.class), ModelPrice.FREE, null, false)), null);
        return List.of(deepSeek, glm, mock);
    }

    public static List<ModelRoute> routes() {
        return List.of(route(Capability.CHAT, GLM_PLUS, DEFAULT_TIMEOUT_MS),
                route(Capability.TOOLS, GLM_PLUS, DEFAULT_TIMEOUT_MS),
                route(Capability.VISION, GLM_VISION, VISION_TIMEOUT_MS));
    }

    private static ModelRoute route(Capability cap, String glmModel, int timeoutMs) {
        return ModelRoute.restore(cap, new RouteTarget(DEEPSEEK_ID, DEEPSEEK_FLASH),
                List.of(new RouteTarget(GLM_ID, glmModel), new RouteTarget(MOCK_ID, MOCK_MODEL)),
                timeoutMs, 1, ThinkingMode.DISABLED, null, "system");
    }

    private static ModelPrice price(String hit, String in, String out) {
        return new ModelPrice(new BigDecimal(hit), new BigDecimal(in), new BigDecimal(out));
    }
}
