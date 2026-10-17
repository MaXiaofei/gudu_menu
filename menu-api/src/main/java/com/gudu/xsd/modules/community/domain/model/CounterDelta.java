package com.gudu.xsd.modules.community.domain.model;

/** 计数增量（可为负：取消赞/收藏）。叠加到 {@link PostCounts} 时再归零截断。 */
public record CounterDelta(long likes, long collects, long comments, long copies) {

    public static final CounterDelta NONE = new CounterDelta(0, 0, 0, 0);

    public static CounterDelta of(PostCounter c, long delta) {
        return NONE.plus(c, delta);
    }

    public CounterDelta plus(PostCounter c, long delta) {
        return switch (c) {
            case LIKE -> new CounterDelta(likes + delta, collects, comments, copies);
            case COLLECT -> new CounterDelta(likes, collects + delta, comments, copies);
            case COMMENT -> new CounterDelta(likes, collects, comments + delta, copies);
            case COPY -> new CounterDelta(likes, collects, comments, copies + delta);
        };
    }

    public boolean isZero() {
        return likes == 0 && collects == 0 && comments == 0 && copies == 0;
    }
}
