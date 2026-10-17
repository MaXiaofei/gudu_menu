package com.gudu.xsd.modules.cookbook.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 做菜记录只读条目（营养报告等按时间段聚合用）。 */
public record CookedDishEntry(Long dishId, Long memberId, BigDecimal servingFactor, LocalDateTime cookedAt) {
}
