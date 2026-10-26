package com.gudu.xsd.modules.contentsecurity.domain.model;

import java.util.Locale;

/**
 * 内容用途：决定微信场景值、检测服务不可用时的失败策略、以及「疑似（review）」是否也拦截。
 *
 * <ul>
 *   <li>{@link #failClosed}：true = 微信检测不可用时拒绝提交（昵称/头像，公开展示且可稍后重试）；
 *       false = 降级到规则兜底（AI 对话、反馈、社区等）。</li>
 *   <li>{@link #blocksReview}：true = 微信判「review 疑似」也拦截（昵称/头像代价低，宁严勿松）。</li>
 * </ul>
 */
public enum ContentPurpose {
    NICKNAME(ContentScene.PROFILE, true, true),
    AVATAR(ContentScene.PROFILE, true, true),
    AGENT_INPUT(ContentScene.SOCIAL_LOG, false, false),
    AGENT_OUTPUT(ContentScene.SOCIAL_LOG, false, false),
    FEEDBACK(ContentScene.COMMENT, false, false),
    POST(ContentScene.FORUM, false, false),
    COMMENT(ContentScene.COMMENT, false, false),
    /** 其它用户上传图片（菜谱封面、食记、拍照识别、评价图…）。 */
    UPLOAD(ContentScene.SOCIAL_LOG, false, false);

    private final ContentScene scene;
    private final boolean failClosed;
    private final boolean blocksReview;

    ContentPurpose(ContentScene scene, boolean failClosed, boolean blocksReview) {
        this.scene = scene;
        this.failClosed = failClosed;
        this.blocksReview = blocksReview;
    }

    public ContentScene scene() {
        return scene;
    }

    public boolean failClosed() {
        return failClosed;
    }

    public boolean blocksReview() {
        return blocksReview;
    }

    /** 上传接口的 usage 参数（avatar / post / …）→ 用途；未知或空一律按普通上传。 */
    public static ContentPurpose ofUploadUsage(String usage) {
        if (usage == null || usage.isBlank()) return UPLOAD;
        return switch (usage.trim().toLowerCase(Locale.ROOT)) {
            case "avatar" -> AVATAR;
            case "post", "community" -> POST;
            case "comment" -> COMMENT;
            case "feedback" -> FEEDBACK;
            default -> UPLOAD;
        };
    }
}
