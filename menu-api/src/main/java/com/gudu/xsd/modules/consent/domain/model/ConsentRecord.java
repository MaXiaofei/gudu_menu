package com.gudu.xsd.modules.consent.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 一次协议同意 / 撤回（只追加，不修改）：哪个账号、哪份文件的哪个版本、同意还是撤回、在哪个端、什么时间。
 * 用于证明「用户同意过哪个版本」，账号合并时整体改指主账号。
 */
public final class ConsentRecord extends AggregateRoot<Long> {

    static final int VERSION_MAX = 32;
    static final int IP_MAX = 64;

    private Long id;
    private final Long accountId;
    private final ConsentDocType docType;
    private final String docVersion;
    private final boolean granted;
    private final ConsentChannel channel;
    private final String clientIp;
    private final LocalDateTime createdAt;

    private ConsentRecord(Long id, Long accountId, ConsentDocType docType, String docVersion, boolean granted,
                          ConsentChannel channel, String clientIp, LocalDateTime createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.docType = docType;
        this.docVersion = docVersion;
        this.granted = granted;
        this.channel = channel == null ? ConsentChannel.UNKNOWN : channel;
        this.clientIp = clientIp;
        this.createdAt = createdAt;
    }

    /** 记一次同意（granted=true）或撤回（granted=false）。 */
    public static ConsentRecord of(Long accountId, ConsentDocType docType, String docVersion, boolean granted,
                                   ConsentChannel channel, String clientIp, LocalDateTime now) {
        if (accountId == null) throw new DomainException("请先登录");
        Objects.requireNonNull(docType, "docType");
        String v = docVersion == null ? "" : docVersion.trim();
        if (v.isEmpty() || v.length() > VERSION_MAX) throw new DomainException("协议版本不合法");
        String ip = clientIp == null || clientIp.isBlank() ? null : clientIp.trim();
        if (ip != null && ip.length() > IP_MAX) ip = ip.substring(0, IP_MAX);
        return new ConsentRecord(null, accountId, docType, v, granted, channel, ip, now);
    }

    public static ConsentRecord rehydrate(Long id, Long accountId, ConsentDocType docType, String docVersion,
                                          boolean granted, ConsentChannel channel, String clientIp,
                                          LocalDateTime createdAt) {
        return new ConsentRecord(id, accountId, docType, docVersion, granted, channel, clientIp, createdAt);
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

    public ConsentDocType docType() {
        return docType;
    }

    public String docVersion() {
        return docVersion;
    }

    public boolean granted() {
        return granted;
    }

    public ConsentChannel channel() {
        return channel;
    }

    public String clientIp() {
        return clientIp;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
