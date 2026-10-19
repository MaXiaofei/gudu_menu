package com.gudu.xsd.common.domain;

import java.util.Collection;

/**
 * 领域事件发布端口（领域层/应用层只依赖本接口，零框架依赖）。
 * 实现见 {@code com.gudu.xsd.common.event.SpringDomainEventPublisher}：包装 Spring ApplicationEventPublisher，
 * 在调用方事务内同步发布，监听器异常向上抛出 → 整个用例事务回滚。
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    default void publishAll(Collection<? extends DomainEvent> events) {
        if (events == null) return;
        events.forEach(this::publish);
    }

    /** 发布聚合根上收集的全部事件（pull 后清空）。 */
    default void publishFrom(AggregateRoot<?> aggregate) {
        if (aggregate != null) publishAll(aggregate.pullDomainEvents());
    }
}
