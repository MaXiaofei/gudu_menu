package com.gudu.xsd.common.domain;

import java.time.Instant;

/**
 * 领域事件（共享内核）。命名用过去时（DishCookedEvent / StockLevelChangedEvent）。
 *
 * <p>传输：进程内同步发布（{@link DomainEventPublisher}），事务内消费，消费失败整体回滚；不引入 MQ。
 */
public interface DomainEvent {

    /** 事件唯一 id（UUID 字符串），可作幂等键。 */
    String eventId();

    /** 事件发生时刻。 */
    Instant occurredOn();
}
