package com.gudu.xsd.modules.identity.domain;

import com.gudu.xsd.common.domain.DomainException;

/** 账号合并冲突：手机号账号已绑定另一个微信（ACCOUNT_WECHAT_CONFLICT），两个账号均不变。 */
public class AccountWechatConflictException extends DomainException {

    public static final String CODE = "ACCOUNT_WECHAT_CONFLICT";

    public AccountWechatConflictException() {
        super("该手机号账号已绑定其他微信，无法合并");
    }
}
