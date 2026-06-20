package com.yanhuo.xsd.modules.dish;

import com.yanhuo.xsd.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 菜品搜索入参（继承分页）。keyword+维度+时间+难度；营养上限筛选 V1 强化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DishSearchDTO extends PageQuery {

    private String keyword;

    private List<Long> cuisineIds;

    private List<Long> tagIds;

    private List<Long> categoryIds;

    private Integer maxMinutes;

    private Integer maxDifficulty;

    /**
     * 营养上限精确筛选：metricId → 上限值。任一指标超限即剔除（1 份基准）。
     * 性能：先按 keyword/维度 SQL 过滤候选，再内存算营养二次过滤，候选池大时慢。
     */
    private Map<Long, BigDecimal> nutritionLimits;
}
