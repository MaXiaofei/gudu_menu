package com.gudu.xsd.common.domain;

import com.gudu.xsd.common.event.SpringDomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 共享内核：聚合根事件收集、事件基类、发布端口默认方法、Spring 同步发布实现、上传地址值对象。 */
class DomainKernelTest {

    static final class Pinged extends AbstractDomainEvent {
    }

    static final class Thing extends AggregateRoot<Long> {
        @Override
        public Long id() {
            return 1L;
        }

        void ping() {
            registerEvent(new Pinged());
        }

        void pingNull() {
            registerEvent(null);
        }
    }

    @Test
    void 聚合根_收集事件_pull后清空() {
        Thing t = new Thing();
        t.ping();
        t.ping();
        t.pingNull();
        assertThat(t.peekDomainEvents()).hasSize(2);
        List<DomainEvent> pulled = t.pullDomainEvents();
        assertThat(pulled).hasSize(2).allMatch(e -> e instanceof Pinged);
        assertThat(t.pullDomainEvents()).isEmpty();
        assertThatThrownBy(() -> pulled.add(new Pinged())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 事件基类_自动生成id与时间_也可显式指定() {
        Pinged a = new Pinged();
        Pinged b = new Pinged();
        assertThat(a.eventId()).isNotBlank().isNotEqualTo(b.eventId());
        assertThat(a.occurredOn()).isNotNull();
        assertThat(a.toString()).contains("Pinged").contains(a.eventId());

        AbstractDomainEvent fixed = new AbstractDomainEvent("e-1", Instant.EPOCH) { };
        assertThat(fixed.eventId()).isEqualTo("e-1");
        assertThat(fixed.occurredOn()).isEqualTo(Instant.EPOCH);
        assertThatThrownBy(() -> new AbstractDomainEvent(null, Instant.EPOCH) { })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 发布端口默认方法_publishFrom按顺序发布并清空() {
        List<DomainEvent> sent = new ArrayList<>();
        DomainEventPublisher pub = sent::add;
        Thing t = new Thing();
        t.ping();
        t.ping();
        pub.publishFrom(t);
        assertThat(sent).hasSize(2);
        assertThat(t.peekDomainEvents()).isEmpty();
        pub.publishFrom(null);
        pub.publishAll(null);
        assertThat(sent).hasSize(2);
    }

    @Test
    void Spring实现_同步转发给ApplicationEventPublisher_忽略null() {
        ApplicationEventPublisher delegate = mock(ApplicationEventPublisher.class);
        SpringDomainEventPublisher pub = new SpringDomainEventPublisher(delegate);
        Pinged e = new Pinged();
        pub.publish(e);
        verify(delegate).publishEvent(e);
        pub.publish(null);
        verify(delegate, never()).publishEvent((Object) null);
    }

    @Test
    void 上传地址_只接受本站uploads相对路径() {
        assertThat(UploadedImageUrl.isValid("/gudu/uploads/2026/10/a_b-1.jpg")).isTrue();
        assertThat(UploadedImageUrl.isValid("/uploads/x.png")).isTrue();
        assertThat(UploadedImageUrl.isValid("https://evil.com/uploads/x.png")).isFalse();
        assertThat(UploadedImageUrl.isValid("/gudu/uploads/../etc/passwd")).isFalse();
        assertThat(UploadedImageUrl.isValid("/static/x.png")).isFalse();
        assertThat(UploadedImageUrl.isValid(null)).isFalse();
        assertThat(UploadedImageUrl.isValid("/uploads/" + "a".repeat(260))).isFalse();
        assertThatThrownBy(() -> new UploadedImageUrl("http://x/y.png"))
                .isInstanceOf(DomainException.class).hasMessageContaining("本站上传");
        assertThat(new UploadedImageUrl("/uploads/ok.jpg").value()).isEqualTo("/uploads/ok.jpg");
    }
}
