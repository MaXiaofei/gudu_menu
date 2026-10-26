package com.gudu.xsd.modules.backoffice.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.Objects;

/** 一条运营后台操作日志（只追加）：谁、何时、对什么、做了什么、原因。 */
public final class OperationLog extends AggregateRoot<Long> {

    static final int REASON_MAX = 200;
    static final int DETAIL_MAX = 500;

    private Long id;
    private final Long operatorId;
    private final OperationAction action;
    private final Long targetId;
    private final String reason;
    private final String detail;
    private final LocalDateTime createdAt;

    private OperationLog(Long id, Long operatorId, OperationAction action, Long targetId, String reason,
                         String detail, LocalDateTime createdAt) {
        this.id = id;
        this.operatorId = operatorId;
        this.action = action;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public static OperationLog record(Long operatorId, OperationAction action, Long targetId, String reason,
                                      String detail, LocalDateTime now) {
        if (operatorId == null) throw new DomainException("缺少操作人");
        Objects.requireNonNull(action, "action");
        if (targetId == null) throw new DomainException("缺少操作对象");
        return new OperationLog(null, operatorId, action, targetId, cut(reason, REASON_MAX), cut(detail, DETAIL_MAX),
                now);
    }

    public static OperationLog rehydrate(Long id, Long operatorId, OperationAction action, Long targetId,
                                         String reason, String detail, LocalDateTime createdAt) {
        return new OperationLog(id, operatorId, action, targetId, reason, detail, createdAt);
    }

    private static String cut(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    @Override
    public Long id() {
        return id;
    }

    public Long operatorId() {
        return operatorId;
    }

    public OperationAction action() {
        return action;
    }

    public OperationTarget targetType() {
        return action.target();
    }

    public Long targetId() {
        return targetId;
    }

    public String reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
