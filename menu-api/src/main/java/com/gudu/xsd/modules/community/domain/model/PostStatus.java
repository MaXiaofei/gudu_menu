package com.gudu.xsd.modules.community.domain.model;

/** 帖子状态：PUBLISHED 公开；REVIEWING 待人工审核（仅作者可见）；HIDDEN 被隐藏（仅作者可见）；DELETED 作者删除。 */
public enum PostStatus {
    PUBLISHED, REVIEWING, HIDDEN, DELETED;

    public static PostStatus of(String s) {
        try {
            return s == null ? PUBLISHED : valueOf(s);
        } catch (IllegalArgumentException e) {
            return PUBLISHED;
        }
    }
}
