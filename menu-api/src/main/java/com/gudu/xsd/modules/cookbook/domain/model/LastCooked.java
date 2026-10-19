package com.gudu.xsd.modules.cookbook.domain.model;

import java.time.LocalDateTime;

/** 某道菜最近一次被做（读模型值对象）：谁做的 + 什么时候。 */
public record LastCooked(Long dishId, Long memberId, LocalDateTime cookedAt) {
}
