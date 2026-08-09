package com.gudu.xsd.modules.review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统一评价页数据（GET /review/menu-overview/{menuId}，V43）。
 * 食集信息 + 食集整体已评状态（我的最近一条）+ 每道菜已评状态（我的最近一条）。
 */
public record MenuReviewOverviewVO(
        Long menuId,
        String menuName,
        LocalDateTime finishedAt,
        Integer dishCount,
        MenuReviewStatus menuReview,
        List<DishReviewStatus> dishes) {

    /** 食集整体评价状态（我的最近一条；未评 reviewed=false）。 */
    public record MenuReviewStatus(boolean reviewed, Integer starRating,
                                   Map<Long, Integer> dimensionScores, LocalDateTime createTime) {
    }

    /** 单道菜评价状态（我的最近一条星级；未评 starRating=null）。 */
    public record DishReviewStatus(Long dishId, String dishName, String coverUrl, Integer starRating) {
    }
}
