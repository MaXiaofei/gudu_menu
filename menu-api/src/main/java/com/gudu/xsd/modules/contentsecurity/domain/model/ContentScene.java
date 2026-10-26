package com.gudu.xsd.modules.contentsecurity.domain.model;

/**
 * 微信内容安全场景值（msgSecCheck / mediaCheckAsync v2 的 scene 参数）：
 * 1 资料、2 评论、3 论坛、4 社交日志。
 */
public enum ContentScene {
    PROFILE(1),
    COMMENT(2),
    FORUM(3),
    SOCIAL_LOG(4);

    private final int wxCode;

    ContentScene(int wxCode) {
        this.wxCode = wxCode;
    }

    public int wxCode() {
        return wxCode;
    }
}
