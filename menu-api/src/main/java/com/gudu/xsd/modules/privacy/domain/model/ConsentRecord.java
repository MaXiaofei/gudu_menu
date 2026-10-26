package com.gudu.xsd.modules.privacy.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 一条同意 / 撤回记录（不可变事实，只追加不修改）：某账号在某时刻对某文档某版本作出同意（granted=true）或撤回（false）。
 * 当前是否同意 = 该账号该类型最新一条记录的 granted。
 */
public record ConsentRecord(Long id, Long accountId, ConsentDocType docType, String docVersion, boolean granted,
                            String channel, String clientIp, LocalDateTime createdAt) {

    static final int VERSION_MAX = 32;
    static final int CHANNEL_MAX = 16;
    static final int IP_MAX = 64;

    public ConsentRecord {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(docType, "docType");
        Objects.requireNonNull(createdAt, "createdAt");
        if (docVersion == null || docVersion.isBlank() || docVersion.length() > VERSION_MAX) {
            throw new DomainException("同意的文档版本不合法");
        }
        channel = clip(channel, CHANNEL_MAX);
        clientIp = clip(clientIp, IP_MAX);
    }

    public static ConsentRecord grant(Long accountId, ConsentDocType type, String version, String channel, String ip,
                                      LocalDateTime now) {
        return new ConsentRecord(null, accountId, type, version, true, channel, ip, now);
    }

    public static ConsentRecord withdraw(Long accountId, ConsentDocType type, String version, String channel, String ip,
                                         LocalDateTime now) {
        return new ConsentRecord(null, accountId, type, version, false, channel, ip, now);
    }

    private static String clip(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
