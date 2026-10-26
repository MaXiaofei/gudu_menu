package com.gudu.xsd.modules.contentsecurity.domain.model;

/**
 * 图片异步检测请求。
 *
 * @param mediaUrl 微信服务器可拉取的公网绝对地址
 * @param scene    场景
 * @param openid   用户 openid（微信 v2 必填）
 */
public record MediaCheckRequest(String mediaUrl, ContentScene scene, String openid) {
}
