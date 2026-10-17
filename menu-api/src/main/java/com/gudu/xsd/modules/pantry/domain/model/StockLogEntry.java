package com.gudu.xsd.modules.pantry.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 档位流水（只追加，值对象）。before/after 为 null 分别表示新建档/删档。
 *
 * @param bestBefore 变动前的保质期快照（V69；做菜用掉时记录，用于「本月少扔」统计，其余为 null）
 */
public record StockLogEntry(Long ingredientId, StockAction action, StockLevel before, StockLevel after,
                            String note, Long refId, LocalDate bestBefore) {

    public StockLogEntry {
        Objects.requireNonNull(ingredientId, "ingredientId");
        Objects.requireNonNull(action, "action");
    }

    public StockLogEntry(Long ingredientId, StockAction action, StockLevel before, StockLevel after,
                         String note, Long refId) {
        this(ingredientId, action, before, after, note, refId, null);
    }

    public static StockLogEntry of(Long ingredientId, StockAction action, StockChange change, String note) {
        return new StockLogEntry(ingredientId, action, change.before(), change.after(), note, null, null);
    }

    /** 做菜用掉：附带变动前的保质期快照。 */
    public static StockLogEntry cooked(Long ingredientId, StockAction action, StockChange change, LocalDate bestBefore) {
        return new StockLogEntry(ingredientId, action, change.before(), change.after(), null, null, bestBefore);
    }
}
