package com.gudu.xsd.modules.contentsecurity.domain.model;

/**
 * 文本检测请求。
 *
 * @param content 待检文本
 * @param scene   场景
 * @param openid  用户 openid（微信 v2 必填；规则实现忽略）
 */
public record TextCheck(String content, ContentScene scene, String openid) {

    public TextCheck {
        content = content == null ? "" : content;
        scene = scene == null ? ContentScene.SOCIAL_LOG : scene;
    }
}
