package com.gudu.xsd.modules.community.domain.model;

/** 帖子计数快照（不可变；负数归零，避免 Redis 增量与回刷交错时出现负值）。 */
public record PostCounts(long likes, long collects, long comments, long copies) {

    public static final PostCounts ZERO = new PostCounts(0, 0, 0, 0);

    public PostCounts {
        likes = Math.max(0, likes);
        collects = Math.max(0, collects);
        comments = Math.max(0, comments);
        copies = Math.max(0, copies);
    }

    public PostCounts plus(CounterDelta d) {
        if (d == null) return this;
        return new PostCounts(likes + d.likes(), collects + d.collects(), comments + d.comments(), copies + d.copies());
    }
}
