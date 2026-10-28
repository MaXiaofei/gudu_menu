package com.gudu.xsd.modules.featureswitch.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * 紧急关停开关清单（sys_switch.switch_key）。只用于出现内容风险时一键关闭，默认开。
 * 厨房圈开关（community.enabled）沿用 community 模块的原实现，不在此列。
 */
public enum FeatureKey {

    /** 咕嘟助手（咕嘟页与全部对话入口）。 */
    AGENT_CHAT("agent.chat.enabled", "咕嘟助手"),
    /** 问咕嘟（跟做模式里的提问）。 */
    AGENT_COOK_ASK("agent.cook_ask.enabled", "问咕嘟");

    private final String key;
    private final String label;

    FeatureKey(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static Optional<FeatureKey> ofKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }
}
