package com.gudu.xsd.modules.pantry.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;

/** 余量档位已变化（before=null 表示新建档）。后续 shopping 缺货自动补项订阅。 */
public final class StockLevelChangedEvent extends AbstractDomainEvent {

    private final Long ingredientId;
    private final StockLevel before;
    private final StockLevel after;

    public StockLevelChangedEvent(Long ingredientId, StockLevel before, StockLevel after) {
        this.ingredientId = ingredientId;
        this.before = before;
        this.after = after;
    }

    public Long ingredientId() {
        return ingredientId;
    }

    public StockLevel before() {
        return before;
    }

    public StockLevel after() {
        return after;
    }
}
