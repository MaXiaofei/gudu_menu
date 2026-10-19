package com.gudu.xsd.modules.pantry.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.pantry.domain.event.StockLevelChangedEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 余量聚合根：每食材一条三档库存 + 保质期（best_before）+ 开封时间（opened_at）。
 *
 * <p>变更只能通过意图明确的行为方法：{@link #restock}（入库）、{@link #adjustLevel}（手动修正）、
 * {@link #markUsedUp}（做菜用完）、{@link #markPartiallyUsed}（做菜用了一些）、{@link #changeBestBefore}。
 * 档位真正变化时登记 {@link StockLevelChangedEvent}。
 */
public final class StockItem extends AggregateRoot<Long> {

    private Long id;
    private final Long ingredientId;
    private StockLevel level;
    private LocalDate bestBefore;
    private LocalDateTime openedAt;

    private StockItem(Long id, Long ingredientId, StockLevel level, LocalDate bestBefore, LocalDateTime openedAt) {
        if (ingredientId == null) throw new DomainException("食材 id 不能为空");
        this.id = id;
        this.ingredientId = ingredientId;
        this.level = level == null ? StockLevel.NONE : level;
        this.bestBefore = bestBefore;
        this.openedAt = openedAt;
    }

    /** 新建档（此前没建档的食材第一次入库/修正）。 */
    public static StockItem create(Long ingredientId, StockLevel level, LocalDate bestBefore) {
        StockItem item = new StockItem(null, ingredientId, level, level == StockLevel.NONE ? null : bestBefore, null);
        item.registerEvent(new StockLevelChangedEvent(ingredientId, null, item.level));
        return item;
    }

    /** 从持久化状态重建（仓储用，不登记事件）。 */
    public static StockItem rehydrate(Long id, Long ingredientId, StockLevel level,
                                      LocalDate bestBefore, LocalDateTime openedAt) {
        return new StockItem(id, ingredientId, level, bestBefore, openedAt);
    }

    /** 入库（采购/补货）：设档位 + 新的保质期（新一批，开封时间清空）。 */
    public StockChange restock(StockLevel newLevel, LocalDate newBestBefore) {
        if (newLevel == null) throw new DomainException("库存档位不合法");
        StockChange change = moveTo(newLevel);
        this.bestBefore = newLevel == StockLevel.NONE ? null : newBestBefore;
        this.openedAt = null;
        return change;
    }

    /** 手动修正档位（保质期不动；修正为「没有」时保质期失去意义一并清除）。 */
    public StockChange adjustLevel(StockLevel newLevel) {
        if (newLevel == null) throw new DomainException("库存档位不合法");
        StockChange change = moveTo(newLevel);
        if (newLevel == StockLevel.NONE) clearShelfState();
        return change;
    }

    /** 改保质期（null = 清除）。已用完的食材不记保质期。 */
    public void changeBestBefore(LocalDate newBestBefore) {
        this.bestBefore = level == StockLevel.NONE ? null : newBestBefore;
    }

    /** 做菜确认·用完了 → 没有（保质期/开封一并清除）。 */
    public StockChange markUsedUp() {
        StockChange change = moveTo(StockLevel.NONE);
        clearShelfState();
        return change;
    }

    /** 做菜确认·用了一些 → 降一档（充足→快用完；快用完/没有不动），首次用记开封时间。 */
    public StockChange markPartiallyUsed(LocalDateTime now) {
        StockChange change = moveTo(level.afterPartialUse());
        if (level != StockLevel.NONE && openedAt == null) this.openedAt = now;
        return change;
    }

    /** 距保质期剩余天数：今天到期=0，已过期为负；未记保质期返回 null。 */
    public Integer daysLeft(LocalDate today) {
        return daysLeft(bestBefore, today);
    }

    /** 是否 days 天内到期（含已过期）；没有保质期或已用完不算。 */
    public boolean isExpiringWithin(int days, LocalDate today) {
        Integer left = daysLeft(today);
        return left != null && level != StockLevel.NONE && left <= days;
    }

    /** 纯函数：bestBefore 相对 today 的剩余天数（null 安全）。 */
    public static Integer daysLeft(LocalDate bestBefore, LocalDate today) {
        if (bestBefore == null || today == null) return null;
        return (int) ChronoUnit.DAYS.between(today, bestBefore);
    }

    /** 仓储插入后回填 id（仅允许一次）。 */
    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    private StockChange moveTo(StockLevel newLevel) {
        StockLevel before = this.level;
        this.level = newLevel;
        StockChange change = new StockChange(before, newLevel);
        if (change.changed()) registerEvent(new StockLevelChangedEvent(ingredientId, before, newLevel));
        return change;
    }

    private void clearShelfState() {
        this.bestBefore = null;
        this.openedAt = null;
    }

    @Override
    public Long id() {
        return id;
    }

    public Long ingredientId() {
        return ingredientId;
    }

    public StockLevel level() {
        return level;
    }

    public LocalDate bestBefore() {
        return bestBefore;
    }

    public LocalDateTime openedAt() {
        return openedAt;
    }
}
