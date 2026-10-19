package com.gudu.xsd.modules.pantry;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存三色分组列表 VO（GET /pantry/grouped，V42 手动 3 档版）。
 * 读 ingredient_stock（每食材一行档位），按 够/低/缺 三色分组返回。
 */
@Data
public class PantryGroupedVO {

    private Summary summary;
    private List<Item> items;

    @Data
    public static class Summary {
        /** 够（ENOUGH）。 */
        private int enough;
        /** 偏低（LOW）。 */
        private int low;
        /** 缺/空（NONE）。 */
        private int none;
    }

    @Data
    public static class Item {
        private Long ingredientId;
        private String ingredientName;
        /** 插画 key（V68，可空：客户端退首字）。 */
        private String iconKey;
        /** ENOUGH 充足 / LOW 快用完 / NONE 没有。 */
        private String level;
        /** 保质期（V64，可空）。 */
        private java.time.LocalDate bestBefore;
        /** 剩余天数：今天到期=0，已过期为负；未记保质期 null。 */
        private Integer daysLeft;
        /** 最近一次变动（用于列表行展示来源标签）。 */
        private LastChange lastChange;
    }

    @Data
    public static class LastChange {
        /** cook 用完了 / cook_partial 用了一些 / purchase 采购 / manual 手动。 */
        private String source;
        private String sourceNote;
        private LocalDateTime createTime;
    }
}
