package com.gudu.xsd.common.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合根基类（共享内核）：行为方法内 {@link #registerEvent} 收集领域事件，
 * 应用服务保存聚合后 {@link #pullDomainEvents()} 取出并交给 {@link DomainEventPublisher} 发布。
 *
 * @param <ID> 聚合标识类型
 */
public abstract class AggregateRoot<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /** 聚合标识（新建未持久化时可为 null）。 */
    public abstract ID id();

    protected final void registerEvent(DomainEvent event) {
        if (event != null) {
            domainEvents.add(event);
        }
    }

    /** 取出并清空已收集的事件（只能取一次，避免重复发布）。 */
    public final List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> out = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return out;
    }

    /** 当前待发布事件（只读视图，测试断言用）。 */
    public final List<DomainEvent> peekDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
