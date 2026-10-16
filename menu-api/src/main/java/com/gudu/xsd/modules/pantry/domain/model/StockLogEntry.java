package com.gudu.xsd.modules.pantry.domain.model;

import java.util.Objects;

/** 档位流水（只追加，值对象）。before/after 为 null 分别表示新建档/删档。 */
public record StockLogEntry(Long ingredientId, StockAction action, StockLevel before, StockLevel after,
                            String note, Long refId) {

    public StockLogEntry {
        Objects.requireNonNull(ingredientId, "ingredientId");
        Objects.requireNonNull(action, "action");
    }

    public static StockLogEntry of(Long ingredientId, StockAction action, StockChange change, String note) {
        return new StockLogEntry(ingredientId, action, change.before(), change.after(), note, null);
    }
}
