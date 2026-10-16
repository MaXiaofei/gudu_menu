package com.gudu.xsd.modules.recommend.application.dto;

import com.gudu.xsd.modules.pantry.application.dto.CoverageView;

/** 契约 DishCard：菜卡（首页智荐分区通用）。 */
public record DishCard(Long dishId, String name, String coverImage, Integer cookMinutes, Integer difficulty,
                       int cookedCount, CoverageView coverage) {
}
