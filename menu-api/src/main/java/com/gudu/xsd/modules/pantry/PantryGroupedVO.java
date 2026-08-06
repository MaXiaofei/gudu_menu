package com.gudu.xsd.modules.pantry;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存三色分组列表 VO（GET /pantry/grouped）。
 * 同食材多笔 pantry 聚合成一项，按 够/低/缺 三色分组返回。
 */
@Data
public class PantryGroupedVO {

    private Summary summary;
    private List<Item> items;

    @Data
    public static class Summary {
        /** 够（有库存且 >= 阈值）。 */
        private int enough;
        /** 偏低（有库存但 < 阈值）。 */
        private int low;
        /** 缺/空（无库存或合计 0）。 */
        private int none;
    }

    @Data
    public static class Item {
        private Long ingredientId;
        private String ingredientName;
        private Long unitId;
        private String unitName;
        /** 食材级阈值（按默认单位计）。 */
        private BigDecimal lowThreshold;

        /** 合计数量（按食材默认单位，聚合后）。 */
        private BigDecimal totalAmount;
        /** 合计克数（聚合 SUM(grams)，三色判定基准）。 */
        private BigDecimal totalGrams;

        /** ENOUGH / LOW / NONE。 */
        private String status;

        /** 最近一次变动（用于列表行展示来源标签）。 */
        private LastChange lastChange;
    }

    @Data
    public static class LastChange {
        /** cook / purchase / inventory / manual。 */
        private String source;
        private String sourceNote;
        private LocalDateTime createTime;
        /** 变动量（克，正入负出）。 */
        private BigDecimal delta;
    }
}
