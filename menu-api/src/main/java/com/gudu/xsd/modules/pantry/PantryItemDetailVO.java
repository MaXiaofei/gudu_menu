package com.gudu.xsd.modules.pantry;

import lombok.Data;

import java.util.List;

/**
 * 食材详情 VO（GET /pantry/item，V42 手动 3 档版）。
 * 食材信息 + 当前档位 + 最近 N 条变动流水（详情页明细时间线）。
 */
@Data
public class PantryItemDetailVO {

    private Long ingredientId;
    private String ingredientName;
    /** ENOUGH 充足 / LOW 快用完 / NONE 没有。 */
    private String level;
    /** 最近变动流水（stock_log，默认 6 条）。 */
    private List<StockLog> changes;
}
