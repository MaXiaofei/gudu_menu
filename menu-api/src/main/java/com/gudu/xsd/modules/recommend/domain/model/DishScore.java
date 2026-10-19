package com.gudu.xsd.modules.recommend.domain.model;

/** 候选菜打分输入（值对象）：覆盖率（enough/total）、缺几样、做过几次。 */
/**
 * 智荐打分输入。
 *
 * @param complete   有封面且有做法步骤（首推、季节鲜只推完整菜谱；不完整的排在最后，仅候选不足时补位）
 * @param urgentDays 用到的快到期食材里最早几天到期（越小越先吃；没用到为 Integer.MAX_VALUE）
 */
public record DishScore(Long dishId, double coverageRatio, int missingCount, int cookedCount, boolean complete,
                        int urgentDays) {

    public DishScore(Long dishId, double coverageRatio, int missingCount, int cookedCount) {
        this(dishId, coverageRatio, missingCount, cookedCount, true, Integer.MAX_VALUE);
    }
}
