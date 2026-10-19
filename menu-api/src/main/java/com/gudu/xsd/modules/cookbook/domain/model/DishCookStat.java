package com.gudu.xsd.modules.cookbook.domain.model;

import java.time.LocalDateTime;

/** 某成员对某道菜的做菜统计（读模型值对象）：最近一次时间 + 次数。 */
public record DishCookStat(Long dishId, LocalDateTime lastCookedAt, int count) {
}
