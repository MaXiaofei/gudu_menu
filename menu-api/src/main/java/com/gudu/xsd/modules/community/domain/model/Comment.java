package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;

/** 评论（聚合根）：最多两级——回复只能挂在顶层评论下。 */
public final class Comment extends AggregateRoot<Long> {

    public static final int MAX_TEXT = 300;

    private Long id;
    private final Long postId;
    private final Long authorId;
    private final Long parentId;
    private final String text;
    private final LocalDateTime createTime;

    private Comment(Long id, Long postId, Long authorId, Long parentId, String text, LocalDateTime createTime) {
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.text = text;
        this.createTime = createTime;
    }

    /**
     * 发表评论。parent 非空时为回复：parent 必须属于同一帖子且本身是顶层评论（两级上限）。
     */
    public static Comment write(Long postId, Long authorId, String text, Comment parent, LocalDateTime now) {
        if (authorId == null) throw new DomainException("请先登录");
        if (postId == null) throw new DomainException("帖子不存在");
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.length() > MAX_TEXT) throw new DomainException("评论需 1–300 字");
        Long parentId = null;
        if (parent != null) {
            if (!postId.equals(parent.postId)) throw new DomainException("回复的评论不属于该帖子");
            if (parent.parentId != null) throw new DomainException("只能回复顶层评论");
            parentId = parent.id;
        }
        return new Comment(null, postId, authorId, parentId, t, now);
    }

    public static Comment rehydrate(Long id, Long postId, Long authorId, Long parentId, String text,
                                    LocalDateTime createTime) {
        return new Comment(id, postId, authorId, parentId, text, createTime);
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    @Override
    public Long id() {
        return id;
    }

    public Long postId() {
        return postId;
    }

    public Long authorId() {
        return authorId;
    }

    public Long parentId() {
        return parentId;
    }

    public String text() {
        return text;
    }

    public LocalDateTime createTime() {
        return createTime;
    }
}
