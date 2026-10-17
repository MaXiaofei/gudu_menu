package com.gudu.xsd.modules.agent.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 撤销凭据：写操作执行后发放，{@link #TTL} 内可用一次；plan 为补偿操作（删除新加的菜/采购项、恢复档位…）。
 */
public record UndoTicket(String token, Long memberId, String tool, String kind, Map<String, Object> plan,
                         LocalDateTime expiresAt) {

    public static final Duration TTL = Duration.ofMinutes(5);

    public UndoTicket {
        plan = plan == null ? Map.of() : Map.copyOf(plan);
    }

    public void assertUsable(Long member, LocalDateTime now) {
        if (!memberId.equals(member)) throw new DomainException("无权撤销");
        if (now.isAfter(expiresAt)) throw new DomainException("已超过可撤销时间");
    }
}
