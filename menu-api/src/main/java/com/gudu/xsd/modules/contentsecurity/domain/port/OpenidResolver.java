package com.gudu.xsd.modules.contentsecurity.domain.port;

import java.util.Optional;

/**
 * 登录账号 → 小程序 openid（微信内容安全 v2 必填）。
 * App / 网页账号没有 openid 时返回 empty，走规则兜底。
 * 多家庭（multi-tenant-family）把 openid 挪到账号表后只需改这个端口的实现。
 */
public interface OpenidResolver {

    Optional<String> openidOf(Long accountId);
}
