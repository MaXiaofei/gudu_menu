package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.privacy.application.ConsentAppService.ConsentHistoryView;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import com.gudu.xsd.modules.privacy.domain.model.ConsentRecord;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B.1 登录协议同意：默认 terms + privacy 各一条、撤回只留痕不发删除事件、单独同意类型不能走此入口、后台倒序历史。 */
class LegalConsentAppServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    final ConsentRepository repo = mock(ConsentRepository.class);
    final DomainEventPublisher events = mock(DomainEventPublisher.class);
    final ConsentAppService svc = new ConsentAppService(repo, events, CLOCK);

    @Test
    void 未传类型_记用户协议与隐私政策_带版本渠道IP() {
        List<ConsentHistoryView> out = svc.recordLegal(7L, null, "2026-10-26", true, "mp-weixin", "1.2.3.4");
        assertThat(out).extracting(ConsentHistoryView::docType).containsExactly("terms", "privacy");
        ArgumentCaptor<ConsentRecord> c = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(repo, times(2)).append(c.capture());
        assertThat(c.getAllValues()).allSatisfy(r -> {
            assertThat(r.accountId()).isEqualTo(7L);
            assertThat(r.granted()).isTrue();
            assertThat(r.docVersion()).isEqualTo("2026-10-26");
            assertThat(r.channel()).isEqualTo("mp-weixin");
            assertThat(r.clientIp()).isEqualTo("1.2.3.4");
            assertThat(r.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 10, 0));
        });
    }

    @Test
    void 撤回_granted_false_不触发敏感信息删除事件() {
        List<ConsentHistoryView> out = svc.recordLegal(7L, List.of("privacy"), "2026-10-26", false, null, null);
        assertThat(out).singleElement().satisfies(v -> {
            assertThat(v.docType()).isEqualTo("privacy");
            assertThat(v.granted()).isFalse();
        });
        verify(events, never()).publish(any());
    }

    @Test
    void 校验_未登录_缺版本_单独同意类型拒绝() {
        assertThatThrownBy(() -> svc.recordLegal(null, null, "v", true, null, null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.recordLegal(7L, null, " ", true, null, null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.recordLegal(7L, List.of("sensitive_health"), "v", true, null, null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.grant(7L, List.of("terms"), "v", null, null))
                .isInstanceOf(com.gudu.xsd.common.domain.DomainException.class);
        verify(repo, never()).append(any());
    }

    @Test
    void 历史_含协议与单独同意_倒序() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 26, 9, 0);
        when(repo.history(7L)).thenReturn(List.of(
                new ConsentRecord(1L, 7L, ConsentDocType.TERMS, "2026-10-01", true, "mp-weixin", null, t),
                new ConsentRecord(2L, 7L, ConsentDocType.SENSITIVE_HEALTH, "2026-10-26", true, null, null, t.plusDays(1))));
        assertThat(svc.history(7L)).extracting(ConsentHistoryView::id).containsExactly(2L, 1L);
    }
}
