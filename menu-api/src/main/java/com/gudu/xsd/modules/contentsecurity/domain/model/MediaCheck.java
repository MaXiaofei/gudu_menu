package com.gudu.xsd.modules.contentsecurity.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaBlockedEvent;

import java.time.LocalDateTime;

/**
 * 一次图片异步检测（聚合根）：上传后提交微信 mediaCheckAsync，记下 trace_id；
 * 消息推送回调到达后 {@link #resolve} 定论，违规时登记 {@link MediaBlockedEvent}（由应用层隔离文件）。
 * 回调幂等：已定论（PASS / REVIEW / RISKY）的检测不再改变。
 */
public final class MediaCheck extends AggregateRoot<Long> {

    private Long id;
    private final String traceId;
    private final String mediaUrl;
    private final Long memberId;
    private final ContentPurpose purpose;
    private MediaCheckStatus status;
    private int label;
    private final LocalDateTime createTime;

    private MediaCheck(Long id, String traceId, String mediaUrl, Long memberId, ContentPurpose purpose,
                       MediaCheckStatus status, int label, LocalDateTime createTime) {
        this.id = id;
        this.traceId = traceId;
        this.mediaUrl = mediaUrl;
        this.memberId = memberId;
        this.purpose = purpose == null ? ContentPurpose.UPLOAD : purpose;
        this.status = status == null ? MediaCheckStatus.PENDING : status;
        this.label = label;
        this.createTime = createTime;
    }

    /** 已提交微信，等待回调。 */
    public static MediaCheck submitted(String traceId, String mediaUrl, Long memberId, ContentPurpose purpose,
                                       LocalDateTime now) {
        if (traceId == null || traceId.isBlank()) throw new DomainException("缺少检测 trace_id");
        if (mediaUrl == null || mediaUrl.isBlank()) throw new DomainException("缺少图片地址");
        return new MediaCheck(null, traceId, mediaUrl, memberId, purpose, MediaCheckStatus.PENDING, 0, now);
    }

    /** 提交失败（微信不可用 / 未配置公网地址）：留痕，头像等失败即拒绝的用途据此拒绝保存。 */
    public static MediaCheck submitFailed(String mediaUrl, Long memberId, ContentPurpose purpose, LocalDateTime now) {
        if (mediaUrl == null || mediaUrl.isBlank()) throw new DomainException("缺少图片地址");
        return new MediaCheck(null, null, mediaUrl, memberId, purpose, MediaCheckStatus.SUBMIT_FAILED, 0, now);
    }

    public static MediaCheck rehydrate(Long id, String traceId, String mediaUrl, Long memberId, ContentPurpose purpose,
                                       MediaCheckStatus status, int label, LocalDateTime createTime) {
        return new MediaCheck(id, traceId, mediaUrl, memberId, purpose, status, label, createTime);
    }

    /**
     * 回调定论。
     *
     * @return true = 本次定论为拦截（需隔离文件）
     */
    public boolean resolve(MediaCheckResult result) {
        if (status != MediaCheckStatus.PENDING && status != MediaCheckStatus.CHECK_FAILED) return false;
        if (result.errcode() != 0) {
            status = MediaCheckStatus.CHECK_FAILED;
            return false;
        }
        label = result.label();
        status = switch (result.suggest()) {
            case PASS -> MediaCheckStatus.PASS;
            case REVIEW -> MediaCheckStatus.REVIEW;
            case RISKY -> MediaCheckStatus.RISKY;
        };
        if (result.suggest().blocks(purpose)) {
            status = MediaCheckStatus.RISKY;
            registerEvent(new MediaBlockedEvent(mediaUrl, memberId, purpose.name(), label));
            return true;
        }
        return false;
    }

    /** 该图片已判违规（引用处不可用）。 */
    public boolean blocked() {
        return status == MediaCheckStatus.RISKY;
    }

    /** 检测未生效（提交失败 / 微信检测失败），需要重新提交。 */
    public boolean needsResubmit() {
        return status == MediaCheckStatus.SUBMIT_FAILED || status == MediaCheckStatus.CHECK_FAILED;
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    @Override
    public Long id() {
        return id;
    }

    public String traceId() {
        return traceId;
    }

    public String mediaUrl() {
        return mediaUrl;
    }

    public Long memberId() {
        return memberId;
    }

    public ContentPurpose purpose() {
        return purpose;
    }

    public MediaCheckStatus status() {
        return status;
    }

    public int label() {
        return label;
    }

    public LocalDateTime createTime() {
        return createTime;
    }
}
