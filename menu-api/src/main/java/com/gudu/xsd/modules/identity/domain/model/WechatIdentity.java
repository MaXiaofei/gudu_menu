package com.gudu.xsd.modules.identity.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 微信身份值对象：openid 必填（同一小程序内唯一），unionid 可空。 */
public record WechatIdentity(String openid, String unionid) {

    public WechatIdentity {
        if (openid == null || openid.isBlank() || openid.length() > 64) throw new DomainException("微信身份无效");
    }

    public static WechatIdentity of(String openid, String unionid) {
        return new WechatIdentity(openid, unionid == null || unionid.isBlank() ? null : unionid);
    }
}
