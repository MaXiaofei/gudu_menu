package com.gudu.xsd.modules.featureswitch.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * 生成式 AI 紧急关停开关的类别（每个类别按端各有一个 sys_switch 行）。
 * 库里的 switch_key = 类别基础键 + "." + 端（mp / app），如 {@code agent.chat.enabled.mp}。
 * 只用于出现内容风险 / 审核要求时一键关闭，默认开。
 * 厨房圈开关（community.enabled）沿用 community 模块的原实现，不在此列。
 */
public enum FeatureKey {

    /** 咕嘟助手（咕嘟页与全部对话入口）。 */
    AGENT_CHAT("agent.chat.enabled", "agentChat", "咕嘟助手"),
    /** 问咕嘟（跟做模式里的提问）。 */
    AGENT_COOK_ASK("agent.cook_ask.enabled", "cookAsk", "问咕嘟"),
    /** 拍照识别（创建识别任务）。 */
    VISION("ai.vision.enabled", "vision", "拍照识别"),
    /** 一周餐程（AI 生成草稿）。 */
    MEAL_PLAN("ai.mealplan.enabled", "mealPlan", "一周餐程");

    private final String baseKey;
    private final String category;
    private final String label;

    FeatureKey(String baseKey, String category, String label) {
        this.baseKey = baseKey;
        this.category = category;
        this.label = label;
    }

    /** 类别基础键（不含端后缀）。 */
    public String baseKey() {
        return baseKey;
    }

    /** sys_switch.switch_key：基础键 + 端后缀。 */
    public String key(Platform platform) {
        return baseKey + "." + platform.code();
    }

    /** 对外类别名（与用户侧接口字段一致）：agentChat / cookAsk / vision / mealPlan。 */
    public String category() {
        return category;
    }

    public String label() {
        return label;
    }

    /** 由完整 switch_key（带端后缀）反查出的类别与端。 */
    public record Resolved(FeatureKey feature, Platform platform) {
    }

    public static Optional<Resolved> resolve(String switchKey) {
        if (switchKey == null) return Optional.empty();
        for (FeatureKey f : values()) {
            for (Platform p : Platform.values()) {
                if (f.key(p).equals(switchKey)) return Optional.of(new Resolved(f, p));
            }
        }
        return Optional.empty();
    }

    public static Optional<FeatureKey> ofBaseKey(String baseKey) {
        return Arrays.stream(values()).filter(k -> k.baseKey.equals(baseKey)).findFirst();
    }
}
