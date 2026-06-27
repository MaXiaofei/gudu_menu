package com.gudu.xsd.modules.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 菜单推荐请求。
 *
 * <p>两种调用形态：
 * <ul>
 *   <li>controller 入参：memberId/budget/scope/cuisineIds/...（候选池由 AiService 查好回填）。</li>
 *   <li>AiService 回填后传给 AiClient：candidates（候选菜上下文）+ healthConstraints（健康约束/过敏）。
 *       DeepSeekAiClient 据此从候选里选菜组合，失败降级 MenuRecommender。</li>
 * </ul>
 *
 * @param memberId         就餐成员（取其 healthProfile 的 constraints/allergies）
 * @param budget           总预算上限（菜单 totalPrice 不得超过）
 * @param scope            DAY / WEEK（候选组数：DAY=1 组，WEEK=3 组）
 * @param cuisineIds       菜系过滤（可空）
 * @param tagIds           标签过滤（可空）
 * @param categoryIds      分类过滤（可空）
 * @param maxMinutes       最大耗时（可空）
 * @param maxDifficulty    最大难度（可空）
 * @param candidates       候选菜上下文（AiService 回填；传给 LLM 选菜）。可空（规则降级时用）。
 * @param healthConstraints 健康约束（sugarMax/giMax/sodiumMax + audiences + allergies）。
 */
public record MenuRecommendRequest(
        Long memberId,
        BigDecimal budget,
        String scope,
        List<Long> cuisineIds,
        List<Long> tagIds,
        List<Long> categoryIds,
        Integer maxMinutes,
        Integer maxDifficulty,
        List<CandidateDish> candidates,
        Map<String, Object> healthConstraints) {

    /** controller 入参便捷构造（candidates / healthConstraints 由 AiService 回填）。 */
    public MenuRecommendRequest(
            Long memberId, BigDecimal budget, String scope,
            List<Long> cuisineIds, List<Long> tagIds, List<Long> categoryIds,
            Integer maxMinutes, Integer maxDifficulty) {
        this(memberId, budget, scope, cuisineIds, tagIds, categoryIds,
                maxMinutes, maxDifficulty, List.of(), Map.of());
    }
}
