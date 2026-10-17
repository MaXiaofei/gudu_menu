package com.gudu.xsd.modules.recommend.application.dto;

import com.gudu.xsd.modules.pantry.application.dto.CoverageView;

/**
 * 契约 DishCard：菜卡（首页智荐分区通用）。
 *
 * @param coverUrl 与 coverImage 同值（prototype-ui-rollout 0.3 统一字段名；客户端改读 coverUrl 后一个版本删 coverImage）
 */
public record DishCard(Long dishId, String name, String coverImage, Integer cookMinutes, Integer difficulty,
                       int cookedCount, CoverageView coverage, String coverUrl) {

    /** coverUrl 自动取 coverImage。 */
    public DishCard(Long dishId, String name, String coverImage, Integer cookMinutes, Integer difficulty,
                    int cookedCount, CoverageView coverage) {
        this(dishId, name, coverImage, cookMinutes, difficulty, cookedCount, coverage, coverImage);
    }
}
