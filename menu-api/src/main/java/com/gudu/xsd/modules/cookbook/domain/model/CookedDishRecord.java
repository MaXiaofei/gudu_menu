package com.gudu.xsd.modules.cookbook.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 做菜记录（聚合根，只追加）：某成员某时做了某道菜几份。由 DishCookedEvent 生成，eventId 作幂等键。
 */
public final class CookedDishRecord extends AggregateRoot<Long> {

    private Long id;
    private final String eventId;
    private final Long memberId;
    private final Long dishId;
    private final Long menuId;
    private final BigDecimal servingFactor;
    private final String source;
    private final String memo;
    private final String note;
    private final LocalDateTime cookedAt;

    private CookedDishRecord(String eventId, Long memberId, Long dishId, Long menuId, BigDecimal servingFactor,
                             String source, String memo, String note, LocalDateTime cookedAt) {
        if (dishId == null) throw new DomainException("菜谱 id 不能为空");
        if (cookedAt == null) throw new DomainException("做菜时间不能为空");
        this.eventId = eventId;
        this.memberId = memberId;
        this.dishId = dishId;
        this.menuId = menuId;
        this.servingFactor = servingFactor;
        this.source = source;
        this.memo = memo;
        this.note = note;
        this.cookedAt = cookedAt;
    }

    /** 记一笔「做过」。 */
    public static CookedDishRecord cooked(String eventId, Long memberId, Long dishId, Long menuId,
                                          BigDecimal servingFactor, String source, String memo, String note,
                                          LocalDateTime cookedAt) {
        return new CookedDishRecord(eventId, memberId, dishId, menuId, servingFactor, source, memo, note, cookedAt);
    }

    /** 仓储插入后回填 id。 */
    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    @Override
    public Long id() {
        return id;
    }

    public String eventId() {
        return eventId;
    }

    public Long memberId() {
        return memberId;
    }

    public Long dishId() {
        return dishId;
    }

    public Long menuId() {
        return menuId;
    }

    public BigDecimal servingFactor() {
        return servingFactor;
    }

    public String source() {
        return source;
    }

    public String memo() {
        return memo;
    }

    public String note() {
        return note;
    }

    public LocalDateTime cookedAt() {
        return cookedAt;
    }
}
