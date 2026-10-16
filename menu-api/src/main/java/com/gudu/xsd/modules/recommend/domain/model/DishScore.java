package com.gudu.xsd.modules.recommend.domain.model;

/** 候选菜打分输入（值对象）：覆盖率（enough/total）、缺几样、做过几次。 */
public record DishScore(Long dishId, double coverageRatio, int missingCount, int cookedCount) {
}
