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
        /** ENOUGH 充足 / LOW 快用完 / NONE 没有。 */
        private String level;
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
