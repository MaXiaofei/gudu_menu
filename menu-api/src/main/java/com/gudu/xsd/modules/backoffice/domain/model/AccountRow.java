package com.gudu.xsd.modules.backoffice.domain.model;

import java.time.LocalDateTime;

/**
 * 运营后台账号列表行（只读模型，F.1）：只含账号元数据与所属家庭数，不含任何家庭私有数据。
 *
 * @param maskedPhone 脱敏手机号（138****0001）
 * @param openidTail  openid 末 6 位（客服按尾号定位用户）
 * @param familyCount 所属（未解散）家庭数
 * @param banReason   非 null = 封禁中
 */
public record AccountRow(Long id, String nickname, String maskedPhone, String openidTail, String status,
                         String platformRole, int familyCount, String banReason, LocalDateTime bannedAt,
                         LocalDateTime createdAt, LocalDateTime deactivatedAt) {

    public boolean banned() {
        return banReason != null;
    }
}
