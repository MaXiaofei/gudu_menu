package com.gudu.xsd.modules.identity.application.dto;

/**
 * 账号对外视图（不含密码哈希 / openid 明文）。
 *
 * @param maskedPhone  脱敏手机号（138****0001），未绑定为 null
 * @param wechatBound  是否已绑定微信
 * @param platformRole USER / OPERATOR / SUPER_ADMIN
 */
public record AccountView(Long id, String nickname, String avatarUrl, String maskedPhone, boolean wechatBound,
                          boolean hasPassword, String platformRole, String status) {

    public boolean superAdmin() {
        return "SUPER_ADMIN".equals(platformRole);
    }
}
