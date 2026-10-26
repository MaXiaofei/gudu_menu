package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.common.domain.DomainEvent;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.contentsecurity.application.dto.ViolationViews;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaRestoredEvent;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckStatus;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaReviewStatus;
import com.gudu.xsd.modules.contentsecurity.domain.model.Suggest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextViolation;
import com.gudu.xsd.modules.contentsecurity.domain.port.UploadQuarantine;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.contentsecurity.domain.repository.TextViolationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F.3 违规复核：图片恢复（改判 + 事件 + 还原文件）/ 确认违规；文本确认；摘录截断。 */
class ContentReviewAppServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0);
    static final String IMG = "/gudu/uploads/original/2026/10/a.png";

    final MediaCheckRepository media = mock(MediaCheckRepository.class);
    final TextViolationRepository texts = mock(TextViolationRepository.class);
    final UploadQuarantine quarantine = mock(UploadQuarantine.class);
    final List<DomainEvent> published = new ArrayList<>();
    final List<Boolean> publishedAsSystem = new ArrayList<>();
    final ContentReviewAppService svc = new ContentReviewAppService(media, texts, quarantine, e -> {
        published.add(e);
        publishedAsSystem.add(TenantContextHolder.isSystem());
    }, CLOCK);

    static MediaCheck risky() {
        return MediaCheck.rehydrate(3L, "t", IMG, 7L, ContentPurpose.AVATAR, MediaCheckStatus.RISKY, 20002, NOW);
    }

    @Test
    void 恢复_改判RESTORED_系统模式发布恢复事件_还原隔离文件_不再视为违规() {
        MediaCheck c = risky();
        when(media.findById(3L)).thenReturn(Optional.of(c));
        when(quarantine.release(IMG)).thenReturn(true);

        ViolationViews.RestoreResult r = svc.restoreMedia(1L, 3L);

        assertThat(r.filesRestored()).isTrue();
        assertThat(r.violation().reviewStatus()).isEqualTo("RESTORED");
        assertThat(r.violation().reviewedBy()).isEqualTo(1L);
        assertThat(c.blocked()).isFalse();
        verify(media).save(c);
        assertThat(published).singleElement().isInstanceOfSatisfying(MediaRestoredEvent.class, e -> {
            assertThat(e.accountId()).isEqualTo(7L);
            assertThat(e.purpose()).isEqualTo("AVATAR");
            assertThat(e.urlVariants()).contains(IMG, "/gudu/uploads/thumbnail/2026/10/a.png");
        });
        assertThat(publishedAsSystem).containsExactly(true);
        assertThatThrownBy(() -> svc.restoreMedia(1L, 3L)).as("重复恢复").isInstanceOf(DomainException.class);
    }

    @Test
    void 确认违规_幂等_已恢复的不能再确认_未判违规的不能复核() {
        MediaCheck c = risky();
        c.confirmViolation(1L, NOW);
        c.confirmViolation(2L, NOW.plusHours(1));
        assertThat(c.reviewStatus()).isEqualTo(MediaReviewStatus.CONFIRMED);
        assertThat(c.reviewedBy()).isEqualTo(1L);
        assertThat(c.blocked()).isTrue();
        c.restore(1L, NOW);
        assertThatThrownBy(() -> c.confirmViolation(1L, NOW)).isInstanceOf(DomainException.class);
        MediaCheck pass = MediaCheck.rehydrate(4L, "t", IMG, 7L, ContentPurpose.UPLOAD, MediaCheckStatus.PASS, 100, NOW);
        assertThatThrownBy(() -> pass.restore(1L, NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> pass.confirmViolation(1L, NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void 确认图片违规_不动文件不发事件() {
        when(media.findById(3L)).thenReturn(Optional.of(risky()));
        assertThat(svc.confirmMedia(1L, 3L).reviewStatus()).isEqualTo("CONFIRMED");
        assertThat(published).isEmpty();
        verify(quarantine, never()).release(IMG);
    }

    @Test
    void 文本违规_只有拦截结论能建_摘录截断100字_确认() {
        TextVerdict risky = new TextVerdict(Suggest.RISKY, 20002, null, TextVerdict.Source.WX, "t1");
        TextViolation v = TextViolation.of(7L, ContentPurpose.FEEDBACK, risky, "字".repeat(150), NOW);
        assertThat(v.excerpt()).hasSize(101).endsWith("…");
        assertThatThrownBy(() -> TextViolation.of(7L, ContentPurpose.FEEDBACK,
                new TextVerdict(Suggest.REVIEW, 0, null, TextVerdict.Source.WX, null), "x", NOW))
                .as("反馈的疑似不拦截，不记违规").isInstanceOf(DomainException.class);

        when(texts.findById(5L)).thenReturn(Optional.of(v));
        ViolationViews.TextViolation out = svc.confirmText(1L, 5L);
        assertThat(out.confirmed()).isTrue();
        assertThat(out.reviewedBy()).isEqualTo(1L);
        verify(texts).save(v);
    }
}
