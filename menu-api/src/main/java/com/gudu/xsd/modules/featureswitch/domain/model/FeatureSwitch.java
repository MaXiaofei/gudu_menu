package com.gudu.xsd.modules.featureswitch.domain.model;

import java.time.LocalDateTime;

/**
 * 一个紧急关停开关的状态（含最后一次修改信息）。
 *
 * @param key           开关键，如 agent.chat.enabled
 * @param enabled       是否开放
 * @param updatedBy     最后操作人 account id（从未切换过为 null）
 * @param updatedByName 最后操作人昵称快照
 * @param updatedAt     最后切换时间
 * @param reason        最后一次切换原因
 */
public record FeatureSwitch(String key, boolean enabled, Long updatedBy, String updatedByName,
                            LocalDateTime updatedAt, String reason) {

    public static final int MAX_REASON = 200;

    /** 库里没有记录时的默认值：开。 */
    public static FeatureSwitch defaultOn(String key) {
        return new FeatureSwitch(key, true, null, null, null, null);
    }

    /** 一次切换（原因去空白、截断到 MAX_REASON；关停必须有原因）。 */
    public static FeatureSwitch change(String key, boolean enabled, Long operatorId, String operatorName,
                                       LocalDateTime at, String reason) {
        if (operatorId == null) throw new IllegalArgumentException("操作人不能为空");
        String r = reason == null ? null : reason.trim();
        if (r != null && r.isEmpty()) r = null;
        if (r != null && r.length() > MAX_REASON) r = r.substring(0, MAX_REASON);
        if (!enabled && r == null) throw new IllegalArgumentException("关停请填写原因");
        return new FeatureSwitch(key, enabled, operatorId, operatorName, at, r);
    }
}
