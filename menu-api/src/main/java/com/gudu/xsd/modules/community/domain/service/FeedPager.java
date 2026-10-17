package com.gudu.xsd.modules.community.domain.service;

import com.gudu.xsd.modules.community.domain.model.FeedCursor;
import com.gudu.xsd.modules.community.domain.model.FeedEntry;
import com.gudu.xsd.modules.community.domain.model.FeedPage;

import java.util.Comparator;
import java.util.List;

/**
 * 游标分页（纯函数）：候选按 (score desc, id desc) 排序，取严格位于游标之后的 size 条。
 * 首页（cursor=null）以 nowEpochMs 为 asOf；之后各页沿用游标里的 asOf，asOf 之后发布的帖子不进入本轮。
 */
public final class FeedPager {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private FeedPager() {
    }

    public static int normalizeSize(Integer size) {
        if (size == null || size < 1) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    public static FeedPage page(List<FeedEntry> candidates, FeedCursor cursor, int size, long nowEpochMs) {
        long asOf = cursor == null ? nowEpochMs : cursor.asOfEpochMs();
        List<FeedEntry> sorted = candidates.stream()
                .filter(e -> e.createdEpochMs() <= asOf)
                .filter(e -> cursor == null || cursor.isAfter(e.score(), e.postId()))
                .sorted(Comparator.comparingDouble(FeedEntry::score).reversed()
                        .thenComparing(Comparator.comparingLong(FeedEntry::postId).reversed()))
                .distinct()
                .limit(size + 1L)
                .toList();
        boolean more = sorted.size() > size;
        List<FeedEntry> pageItems = more ? sorted.subList(0, size) : sorted;
        FeedCursor next = null;
        if (more) {
            FeedEntry last = pageItems.get(pageItems.size() - 1);
            next = new FeedCursor(last.score(), last.postId(), asOf);
        }
        return new FeedPage(pageItems.stream().map(FeedEntry::postId).toList(), next);
    }
}
