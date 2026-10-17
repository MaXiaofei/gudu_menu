package com.gudu.xsd.modules.community.domain.model;

import java.util.List;

/** 一页 Feed：帖子 id（已排序）+ 下一页游标（null = 没有更多）。 */
public record FeedPage(List<Long> postIds, FeedCursor next) {

    public FeedPage {
        postIds = postIds == null ? List.of() : List.copyOf(postIds);
    }
}
