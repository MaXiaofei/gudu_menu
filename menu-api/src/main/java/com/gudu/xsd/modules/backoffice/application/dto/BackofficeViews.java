package com.gudu.xsd.modules.backoffice.application.dto;

import com.gudu.xsd.modules.backoffice.domain.model.AccountRow;

import java.time.LocalDateTime;
import java.util.List;

/** 运营后台视图。 */
public final class BackofficeViews {

    private BackofficeViews() {
    }

    public record Page<T>(List<T> records, long total) {
    }

    /** 账号列表行：封禁中时 banned=true。 */
    public record AccountItem(Long id, String nickname, String maskedPhone, String openidTail, String status,
                              String platformRole, int familyCount, boolean banned, String banReason,
                              LocalDateTime bannedAt, LocalDateTime createdAt, LocalDateTime deactivatedAt) {

        public static AccountItem of(AccountRow r) {
            return new AccountItem(r.id(), r.nickname(), r.maskedPhone(), r.openidTail(), r.status(), r.platformRole(),
                    r.familyCount(), r.banned(), r.banReason(), r.bannedAt(), r.createdAt(), r.deactivatedAt());
        }
    }

    public record OperationLogItem(Long id, Long operatorId, String operatorName, String action, String actionLabel,
                                   String targetType, Long targetId, String reason, String detail,
                                   LocalDateTime createdAt) {
    }
}
