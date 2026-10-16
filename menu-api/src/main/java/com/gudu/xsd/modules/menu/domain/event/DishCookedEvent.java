package com.gudu.xsd.modules.menu.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSource;
import com.gudu.xsd.modules.menu.domain.model.CookedDish;

import java.util.List;

/**
 * 菜做好了（单菜直做 / 整集做共用）。同步、事务内消费：
 * <ul>
 *   <li>cookbook：每道菜写一条 cooking_record（event_id 幂等），食记可见；</li>
 *   <li>pantry：usedUp → 没有，partiallyUsed → 降一档。</li>
 * </ul>
 */
public final class DishCookedEvent extends AbstractDomainEvent {

    private final Long memberId;
    private final CookSource source;
    private final Long menuId;
    private final List<CookedDish> dishes;
    private final List<Long> usedUp;
    private final List<Long> partiallyUsed;
    private final String note;

    public DishCookedEvent(Long memberId, CookSource source, Long menuId, List<CookedDish> dishes,
                           List<Long> usedUp, List<Long> partiallyUsed, String note) {
        this.memberId = memberId;
        this.source = source;
        this.menuId = menuId;
        this.dishes = dishes == null ? List.of() : List.copyOf(dishes);
        this.usedUp = usedUp == null ? List.of() : List.copyOf(usedUp);
        this.partiallyUsed = partiallyUsed == null ? List.of() : List.copyOf(partiallyUsed);
        this.note = note;
    }

    public Long memberId() {
        return memberId;
    }

    public CookSource source() {
        return source;
    }

    public Long menuId() {
        return menuId;
    }

    public List<CookedDish> dishes() {
        return dishes;
    }

    public List<Long> usedUp() {
        return usedUp;
    }

    public List<Long> partiallyUsed() {
        return partiallyUsed;
    }

    public String note() {
        return note;
    }
}
