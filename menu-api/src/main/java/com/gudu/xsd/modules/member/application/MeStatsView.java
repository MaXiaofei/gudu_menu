package com.gudu.xsd.modules.member.application;

/**
 * GET /me/stats（prototype-ui-rollout 0.7）：「我的」页三格统计。
 *
 * @param cookedThisMonth 本月做饭顿数（同一次做菜事件算一顿）
 * @param familyDishes    家庭菜谱数（不含批量导入的公共库内容）
 * @param savedFromWaste  本月少扔：保质期前 3 天内被做菜用掉的食材次数
 */
public record MeStatsView(long cookedThisMonth, long familyDishes, long savedFromWaste) {
}
