package com.gudu.xsd.modules.recommend.application.dto;

import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;

import java.util.List;

/**
 * GET /recommend/home：任一分区无数据为空数组（前端隐藏该分区）。
 *
 * @param popular      大家常做（全站做菜次数聚合，不针对个人；关闭个性化推荐时代替「你常做」）
 * @param personalized false = 用户关闭了个性化推荐，本次只给通用推荐（不读库存、做饭记录、成员档案）
 */
public record HomeRecommendView(List<ExpiringItemView> expiring, List<DishCard> expiringPicks,
                                List<DishCard> frequent, List<DishCard> seasonal, List<DishCard> popular,
                                boolean personalized) {

    /** 个性化结果（无「大家常做」分区）。 */
    public HomeRecommendView(List<ExpiringItemView> expiring, List<DishCard> expiringPicks,
                             List<DishCard> frequent, List<DishCard> seasonal) {
        this(expiring, expiringPicks, frequent, seasonal, List.of(), true);
    }
}
