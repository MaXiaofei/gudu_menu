package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.contentsecurity.application.dto.ViolationViews;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextViolation;
import com.gudu.xsd.modules.contentsecurity.domain.port.UploadQuarantine;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.contentsecurity.domain.repository.TextViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 内容安全违规人工复核（mini-release-compliance F.3）：查看文本 / 图片违规记录；图片可「恢复」（误判）或「确认违规」，
 * 文本只能「确认违规」（被拦截的文本本就没保存）。操作日志由运营后台应用服务记录。
 *
 * <p>恢复图片：改判 RESTORED → 发布 MediaRestoredEvent（系统模式，同事务：头像恢复、帖子恢复公开）→ 订阅方都成功后
 * 把隔离文件移回原地址（覆盖占位图）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentReviewAppService {

    static final int MAX_PAGE = 100;

    private final MediaCheckRepository mediaChecks;
    private final TextViolationRepository texts;
    private final UploadQuarantine quarantine;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ViolationViews.Page<ViolationViews.MediaViolation> mediaViolations(String review, String purpose,
                                                                              Long accountId, int page, int size) {
        int s = clamp(size);
        int offset = (Math.max(1, page) - 1) * s;
        return new ViolationViews.Page<>(mediaChecks.pageViolations(review, purpose, accountId, offset, s).stream()
                .map(ContentReviewAppService::view).toList(), mediaChecks.countViolations(review, purpose, accountId));
    }

    public ViolationViews.Page<ViolationViews.TextViolation> textViolations(Boolean confirmed, String purpose,
                                                                            Long accountId, int page, int size) {
        int s = clamp(size);
        int offset = (Math.max(1, page) - 1) * s;
        return new ViolationViews.Page<>(texts.page(confirmed, purpose, accountId, offset, s).stream()
                .map(ContentReviewAppService::view).toList(), texts.count(confirmed, purpose, accountId));
    }

    /** 隔离中的原图（复核查看）。 */
    public Optional<byte[]> quarantinedImage(Long mediaCheckId) {
        return mediaChecks.findById(mediaCheckId).flatMap(c -> quarantine.readQuarantined(c.mediaUrl()));
    }

    public ViolationViews.MediaViolation media(Long id) {
        return view(loadMedia(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ViolationViews.RestoreResult restoreMedia(Long operatorId, Long id) {
        MediaCheck c = loadMedia(id);
        c.restore(operatorId, LocalDateTime.now(clock));
        mediaChecks.save(c);
        TenantContextHolder.runAsSystem(() -> events.publishFrom(c));
        boolean restored = quarantine.release(c.mediaUrl());
        log.warn("违规图片人工复核恢复 id={} url={} operator={} filesRestored={}", id, c.mediaUrl(), operatorId, restored);
        return new ViolationViews.RestoreResult(view(c), restored);
    }

    @Transactional(rollbackFor = Exception.class)
    public ViolationViews.MediaViolation confirmMedia(Long operatorId, Long id) {
        MediaCheck c = loadMedia(id);
        c.confirmViolation(operatorId, LocalDateTime.now(clock));
        mediaChecks.save(c);
        return view(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public ViolationViews.TextViolation confirmText(Long operatorId, Long id) {
        TextViolation v = texts.findById(id).orElseThrow(() -> BizException.notFound("违规记录不存在"));
        v.confirm(operatorId, LocalDateTime.now(clock));
        texts.save(v);
        return view(v);
    }

    private MediaCheck loadMedia(Long id) {
        return mediaChecks.findById(id).orElseThrow(() -> BizException.notFound("违规记录不存在"));
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE));
    }

    static ViolationViews.MediaViolation view(MediaCheck c) {
        return new ViolationViews.MediaViolation(c.id(), c.mediaUrl(), c.memberId(), c.purpose().name(),
                c.status().name(), c.label(), c.createTime(),
                c.reviewStatus() == null ? null : c.reviewStatus().name(), c.reviewedBy(), c.reviewedAt());
    }

    static ViolationViews.TextViolation view(TextViolation v) {
        return new ViolationViews.TextViolation(v.id(), v.accountId(), v.purpose().name(), v.suggest().name(),
                v.label(), v.source().name(), v.excerpt(), v.createdAt(), v.confirmed(), v.reviewedBy(),
                v.reviewedAt());
    }
}
