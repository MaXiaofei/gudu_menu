package com.yanhuo.xsd.modules.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜单推荐请求。
 *
 * @param memberId     就餐成员（取其 healthProfile 的 constraints/allergies）
 * @param budget       总预算上限（菜单 totalPrice 不得超过）
 * @param scope        DAY / WEEK（候选组数：DAY=1 组，WEEK=3 组）
 * @param cuisineIds   菜系过滤（可空）
 * @param tagIds       标签过滤（可空）
 * @param categoryIds  分类过滤（可空）
 * @param maxMinutes   最大耗时（可空）
 * @param maxDifficulty 最大难度（可空）
 */
public record MenuRecommendRequest(
        Long memberId,
        BigDecimal budget,
        String scope,
        List<Long> cuisineIds,
        List<Long> tagIds,
        List<Long> categoryIds,
        Integer maxMinutes,
        Integer maxDifficulty) {
}
