package com.gudu.xsd.modules.cookbook.domain.model;

import java.time.LocalDateTime;

/** 做菜记录的只读引用（跨上下文分享用）：只暴露归属、菜与时间，不含备注/食用成员等家庭信息。 */
public record CookedRecordRef(Long id, Long memberId, Long dishId, LocalDateTime cookedAt) {
}
