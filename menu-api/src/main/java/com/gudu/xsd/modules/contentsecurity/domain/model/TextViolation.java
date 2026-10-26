package com.gudu.xsd.modules.contentsecurity.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;

/**
 * 一条文本违规记录（F.3）：被内容安全拦下的文本（昵称、反馈、AI 对话输入输出、帖子、评论）。
 * 只存前 {@value #EXCERPT_MAX} 字摘录供复核（最小必要），不存全文。文本本就未保存 / 已替换，复核只有「确认违规」。
 */
public final class TextViolation extends AggregateRoot<Long> {

    public static final int EXCERPT_MAX = 100;

    private Long id;
    private final Long accountId;
    private final ContentPurpose purpose;
    private final Suggest suggest;
    private final int label;
    private final TextVerdict.Source source;
    private final String excerpt;
    private final LocalDateTime createdAt;
    private boolean confirmed;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    private TextViolation(Long id, Long accountId, ContentPurpose purpose, Suggest suggest, int label,
                          TextVerdict.Source source, String excerpt, LocalDateTime createdAt, boolean confirmed,
                          Long reviewedBy, LocalDateTime reviewedAt) {
        this.id = id;
        this.accountId = accountId;
        this.purpose = purpose;
        this.suggest = suggest;
        this.label = label;
        this.source = source;
        this.excerpt = excerpt;
        this.createdAt = createdAt;
        this.confirmed = confirmed;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    /** 从拦截结论建记录；不拦截的结论不应建记录。 */
    public static TextViolation of(Long accountId, ContentPurpose purpose, TextVerdict verdict, String text,
                                   LocalDateTime now) {
        if (purpose == null || verdict == null || !verdict.blocks(purpose)) {
            throw new DomainException("未拦截的内容不记违规");
        }
        return new TextViolation(null, accountId, purpose, verdict.suggest(), verdict.label(), verdict.source(),
                excerpt(text), now, false, null, null);
    }

    public static TextViolation rehydrate(Long id, Long accountId, ContentPurpose purpose, Suggest suggest, int label,
                                          TextVerdict.Source source, String excerpt, LocalDateTime createdAt,
                                          boolean confirmed, Long reviewedBy, LocalDateTime reviewedAt) {
        return new TextViolation(id, accountId, purpose, suggest, label, source, excerpt, createdAt, confirmed,
                reviewedBy, reviewedAt);
    }

    static String excerpt(String text) {
        if (text == null) return null;
        String t = text.strip();
        return t.codePointCount(0, t.length()) <= EXCERPT_MAX ? t
                : t.substring(0, t.offsetByCodePoints(0, EXCERPT_MAX)) + "…";
    }

    /** 复核确认违规（幂等）。 */
    public void confirm(Long operatorId, LocalDateTime now) {
        if (confirmed) return;
        confirmed = true;
        reviewedBy = operatorId;
        reviewedAt = now;
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    @Override
    public Long id() {
        return id;
    }

    public Long accountId() {
        return accountId;
    }

    public ContentPurpose purpose() {
        return purpose;
    }

    public Suggest suggest() {
        return suggest;
    }

    public int label() {
        return label;
    }

    public TextVerdict.Source source() {
        return source;
    }

    public String excerpt() {
        return excerpt;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public boolean confirmed() {
        return confirmed;
    }

    public Long reviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime reviewedAt() {
        return reviewedAt;
    }
}
