package com.gudu.xsd.modules.pantry.domain.model;

/**
 * 一次档位变动（值对象）：before 为 null 表示新建档。
 */
public record StockChange(StockLevel before, StockLevel after) {

    public boolean changed() {
        return before != after;
    }
}
