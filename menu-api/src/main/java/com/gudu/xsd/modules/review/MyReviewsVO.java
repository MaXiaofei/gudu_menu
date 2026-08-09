package com.gudu.xsd.modules.review;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 我的评价（GET /review/mine，V43）：评价历史 + 待评价食集。
 */
public record MyReviewsVO(
        List<ReviewEntry> reviews,
        List<PendingMenu> pendingMenus) {

    /** 评价历史条目（食集或菜品，名称/星级/时间）。 */
    public record ReviewEntry(Long id, Long dishId, Long menuId, String name,
                              Integer starRating, LocalDateTime createTime) {
    }

    /** 待评价食集：已完成但食集整体没评、或还有菜没评。 */
    public record PendingMenu(Long menuId, String menuName, LocalDateTime finishedAt,
                              int dishCount, int reviewedDishCount, boolean menuReviewed) {
    }
}
