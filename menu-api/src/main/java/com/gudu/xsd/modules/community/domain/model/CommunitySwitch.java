package com.gudu.xsd.modules.community.domain.model;

import java.time.LocalDateTime;

/**
 * 厨房圈紧急关停开关（A.12）：只用于出现内容风险时一键关闭，默认开。
 *
 * @param enabled       是否开放（关闭时社区读写接口统一返回「厨房圈暂停服务」）
 * @param updatedBy     最后操作人 member.id（从未切换过为 null）
 * @param updatedByName 最后操作人昵称快照
 * @param updatedAt     最后切换时间
 * @param reason        最后一次切换原因
 */
public record CommunitySwitch(boolean enabled, Long updatedBy, String updatedByName, LocalDateTime updatedAt,
                              String reason) {

    public static final String KEY = "community.enabled";
    public static final int MAX_REASON = 200;

    /** 库里没有记录时的默认值：开。 */
    public static CommunitySwitch defaultOn() {
        return new CommunitySwitch(true, null, null, null, null);
    }

    /** 一次切换（原因去空白、截断到 MAX_REASON）。 */
    public static CommunitySwitch change(boolean enabled, Long operatorId, String operatorName, LocalDateTime at,
                                         String reason) {
        if (operatorId == null) throw new IllegalArgumentException("操作人不能为空");
        String r = reason == null ? null : reason.trim();
        if (r != null && r.isEmpty()) r = null;
        if (r != null && r.length() > MAX_REASON) r = r.substring(0, MAX_REASON);
        return new CommunitySwitch(enabled, operatorId, operatorName, at, r);
    }
}
