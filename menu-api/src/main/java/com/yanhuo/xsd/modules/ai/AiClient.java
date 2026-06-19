package com.yanhuo.xsd.modules.ai;

import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import com.yanhuo.xsd.modules.ai.dto.MenuRecommendRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;

import java.util.List;

/**
 * AI 能力策略接口（参照 NotificationChannel 范式）。
 *
 * <p>实现：{@code MockAiClient}（规则表兜底，默认）/ {@code GlmAiClient}（GLM 接入预留，空壳）。
 * 由配置 {@code yanhuo.ai.provider=mock|glm} 切换。接口形态钉死，后续 GLM 填实现不改调用方。
 */
public interface AiClient {

    /** 营养补全：按食材名返回 per 100g 的 6 项指标值（参考中国食物成分表 / 分类兜底）。 */
    NutritionFillResponse fillNutrition(NutritionFillRequest req);

    /** 菜单推荐：基于候选菜池 + 健康约束 + 预算，输出若干组候选菜单。 */
    List<MenuCandidate> recommendMenu(MenuRecommendRequest req);

    /** provider 标识（mock/glm），用于日志与来源标注。 */
    default String provider() {
        return "unknown";
    }
}
