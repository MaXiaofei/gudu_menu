package com.gudu.xsd.modules.ai;

import com.gudu.xsd.modules.ai.dto.DishEstimateRequest;
import com.gudu.xsd.modules.ai.dto.DishEstimateResponse;
import com.gudu.xsd.modules.ai.dto.MenuCandidate;
import com.gudu.xsd.modules.ai.dto.MenuRecommendRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillResponse;

import java.util.List;


/**
 * AI 能力策略接口（参照 NotificationChannel 范式）。
 *
 * <p>实现：{@code MockAiClient}（规则表兜底，无需 key）/ {@code DeepSeekAiClient} /
 * {@code GlmAiClient}（均 OpenAI 兼容协议，共用 {@code OpenAiCompatibleClient} 基类）。
 * 三个实现常驻运行，由 {@link AiClientRouter}（标 {@code @Primary}，{@code AiService} 注入它）
 * 按「当前 provider」运行时委托，支持 {@code PUT /ai/provider} 热切换。接口形态钉死，加新厂商不改调用方。
 */
public interface AiClient {

    /** 营养补全：按食材名返回 per 100g 的 6 项指标值（参考中国食物成分表 / 分类兜底）。 */
    NutritionFillResponse fillNutrition(NutritionFillRequest req);

    /** 菜品/一餐营养估算：根据文字描述估算该餐总营养（V2 方案2，纯文本）。 */
    DishEstimateResponse estimateDish(DishEstimateRequest req);

    /** provider 标识（mock/glm），用于日志与来源标注。 */
    default String provider() {
        return "unknown";
    }
}
