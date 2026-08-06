package com.gudu.xsd.modules.pantry;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 食材详情 VO（GET /pantry/item）。
 * 食材信息 + 当前合计 + 最近 N 条变动流水（详情页明细时间线）。
 */
@Data
public class PantryItemDetailVO {

    private Long ingredientId;
    private String ingredientName;
    private Long unitId;
    private String unitName;
    /** 食材级阈值（按默认单位计）。 */
    private BigDecimal lowThreshold;

    /** 当前合计数量（按食材默认单位）。 */
    private BigDecimal totalAmount;
    /** 当前合计克数。 */
    private BigDecimal totalGrams;

    /** 阈值克数（阈值换算成克，前端可显示「系统差 ±N」）。 */
    private BigDecimal thresholdGrams;

    /** ENOUGH / LOW / NONE。 */
    private String status;

    /** 最近变动流水（默认 6 条）。 */
    private List<PantryChangeLog> changes;
}
