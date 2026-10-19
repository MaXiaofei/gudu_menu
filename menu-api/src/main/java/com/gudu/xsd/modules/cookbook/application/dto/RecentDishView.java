package com.gudu.xsd.modules.cookbook.application.dto;

import java.time.LocalDateTime;

/** 最近做过（GET /cookbook/recent）：按菜去重，最近优先。 */
public record RecentDishView(Long dishId, String name, String coverImage, LocalDateTime lastCookedAt,
                             int cookedCount) {
}
