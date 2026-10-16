package com.gudu.xsd.common.event;

import com.gudu.xsd.common.domain.DomainEvent;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * {@link DomainEventPublisher} 的 Spring 实现：同步 {@code publishEvent}，
 * 监听方（{@code @EventListener}）与发布方同线程、同事务执行；任一监听器抛异常 → 发布方事务回滚。
 * 不使用 {@code @Async}/{@code AFTER_COMMIT}，保证「做菜 → 写食记 → 改档位」原子。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    @Override
    public void publish(DomainEvent event) {
        if (event == null) return;
        log.debug("发布领域事件 {}", event);
        delegate.publishEvent(event);
    }
}
