package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.ConsentRequiredException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.privacy.domain.event.SensitiveConsentWithdrawn;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import com.gudu.xsd.modules.privacy.domain.model.ConsentRecord;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConsentAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 9, 0);

    /** 内存仓储：只追加，最新一条决定状态。 */
    static class MemRepo implements ConsentRepository {
        final List<ConsentRecord> rows = new ArrayList<>();

        @Override
        public void append(ConsentRecord r) {
            rows.add(new ConsentRecord((long) rows.size() + 1, r.accountId(), r.docType(), r.docVersion(), r.granted(),
                    r.channel(), r.clientIp(), r.createdAt()));
        }

        @Override
        public Optional<ConsentRecord> latest(Long accountId, ConsentDocType type) {
            return rows.stream().filter(r -> r.accountId().equals(accountId) && r.docType() == type)
                    .max(Comparator.comparing(ConsentRecord::id));
        }

        @Override
        public List<ConsentRecord> history(Long accountId) {
            return rows.stream().filter(r -> r.accountId().equals(accountId)).toList();
        }

        @Override
        public int reassignAccount(Long from, Long to) {
            return 0;
        }
    }

    MemRepo repo;
    DomainEventPublisher events;
    ConsentAppService svc;

    @BeforeEach
    void setUp() {
        repo = new MemRepo();
        events = mock(DomainEventPublisher.class);
        svc = new ConsentAppService(repo, events, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void 未同意_保存健康档案_抛428_列出缺少的类型() {
        assertThatThrownBy(() -> svc.assertAllowed(7L, Map.of("height", 170), null, null))
                .isInstanceOf(ConsentRequiredException.class)
                .satisfies(e -> {
                    ConsentRequiredException c = (ConsentRequiredException) e;
                    assertThat(c.getCode()).isEqualTo(BizException.CONSENT_REQUIRED);
                    assertThat(c.docTypes()).containsExactly("sensitive_health");
                });
        assertThatThrownBy(() -> svc.assertAllowed(7L, Map.of("ageMonth", 10), null, null))
                .isInstanceOf(ConsentRequiredException.class)
                .hasMessageContaining("监护人")
                .satisfies(e -> assertThat(((ConsentRequiredException) e).docTypes())
                        .containsExactlyInAnyOrder("sensitive_health", "child_info"));
        assertThatCode(() -> svc.assertAllowed(7L, Map.of(), null, null)).as("无敏感信息不拦").doesNotThrowAnyException();
        assertThatThrownBy(() -> svc.assertAllowed(null, Map.of("height", 1), null, null))
                .isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void 同意后放行_儿童还需监护人同意() {
        svc.grant(7L, List.of("sensitive_health"), null, "mp-weixin", "1.2.3.4");
        assertThatCode(() -> svc.assertAllowed(7L, Map.of("height", 170), "LOSE", null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> svc.assertAllowed(7L, Map.of("audiences", List.of("宝宝辅食")), null, null))
                .satisfies(e -> assertThat(((ConsentRequiredException) e).docTypes()).containsExactly("child_info"));
        svc.grant(7L, List.of("child_info", "child_info"), "2026-10-26", "mp-weixin", "1.2.3.4");
        assertThatCode(() -> svc.assertAllowed(7L, Map.of("audiences", List.of("宝宝辅食")), null, null))
                .doesNotThrowAnyException();
        assertThat(repo.rows).hasSize(2);
        ConsentRecord r = repo.rows.get(0);
        assertThat(r.docVersion()).isEqualTo(ConsentAppService.SENSITIVE_DOC_VERSION);
        assertThat(r.channel()).isEqualTo("mp-weixin");
        assertThat(r.clientIp()).isEqualTo("1.2.3.4");
        assertThat(r.createdAt()).isEqualTo(NOW);
        assertThat(svc.isGranted(8L, ConsentDocType.SENSITIVE_HEALTH)).as("按账号隔离").isFalse();
    }

    @Test
    void 撤回_追加撤回记录_发布事件_之后再保存被拒() {
        svc.grant(7L, List.of("sensitive_health"), null, "mp-weixin", null);
        var status = svc.withdraw(7L, "sensitive_health", "mp-weixin", "1.2.3.4");
        assertThat(status.items().get("sensitive_health").granted()).isFalse();
        assertThat(repo.rows).hasSize(2);
        assertThat(repo.rows.get(1).granted()).isFalse();
        ArgumentCaptor<SensitiveConsentWithdrawn> ev = ArgumentCaptor.forClass(SensitiveConsentWithdrawn.class);
        verify(events).publish(ev.capture());
        assertThat(ev.getValue().accountId()).isEqualTo(7L);
        assertThat(ev.getValue().docType()).isEqualTo(ConsentDocType.SENSITIVE_HEALTH);
        assertThatThrownBy(() -> svc.assertAllowed(7L, Map.of("height", 170), null, null))
                .isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void 状态_未登录与非法类型() {
        assertThat(svc.status(7L).items()).containsOnlyKeys("sensitive_health", "child_info");
        assertThat(svc.status(7L).items().get("child_info").granted()).isFalse();
        assertThatThrownBy(() -> svc.grant(null, List.of("sensitive_health"), null, null, null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.grant(7L, List.of(), null, null, null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.withdraw(7L, "terms", null, null))
                .isInstanceOf(com.gudu.xsd.common.domain.DomainException.class);
        verify(events, never()).publish(any());
    }
}
