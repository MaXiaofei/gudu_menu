package com.gudu.xsd.modules.ai.dto;

import java.util.List;

/**
 * 菜单推荐请求（2026-08 全向量化：无 LLM，召回/组合在 AiService 内完成）。
 *
 * @param memberId      就餐成员（取其 healthProfile 的 constraints/allergies + 口味画像）
 * @param scope         DAY / WEEK（候选组数：DAY=1 组，WEEK=3 组）
 * @param preference    自然语言偏好（可空，如「清淡下饭」「酸甜口」）——参与向量召回查询
 * @param cuisineIds    菜系过滤（可空，预留）
 * @param tagIds        标签过滤（可空，预留）
 * @param categoryIds   分类过滤（可空，预留）
 * @param maxMinutes    最大耗时（可空，向量检索 metadata 过滤）
 * @param maxDifficulty 最大难度（可空，向量检索 metadata 过滤）
 */
public record MenuRecommendRequest(
        Long memberId,
        String scope,
        String preference,
        List<Long> cuisineIds,
        List<Long> tagIds,
        List<Long> categoryIds,
        Integer maxMinutes,
        Integer maxDifficulty) {
}
