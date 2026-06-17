package com.yanhuo.xsd.modules.dish;

import com.yanhuo.xsd.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

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
}
