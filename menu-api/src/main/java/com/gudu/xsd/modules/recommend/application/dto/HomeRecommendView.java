package com.gudu.xsd.modules.recommend.application.dto;

import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;

import java.util.List;

/** GET /recommend/home：任一分区无数据为空数组（前端隐藏该分区）。 */
public record HomeRecommendView(List<ExpiringItemView> expiring, List<DishCard> expiringPicks,
                                List<DishCard> frequent, List<DishCard> seasonal) {
}
