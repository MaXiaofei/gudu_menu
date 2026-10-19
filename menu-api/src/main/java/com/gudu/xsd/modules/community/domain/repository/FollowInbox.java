package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.FeedEntry;

import java.util.Collection;
import java.util.List;

/**
 * 关注流收件箱（写扩散）：每个用户一个按发布时间排序的帖子集合，只保留最近 {@link #CAPACITY} 条。
 * 粉丝数超过阈值的作者不写扩散，读时合并（读扩散），见应用层。
 */
public interface FollowInbox {

    int CAPACITY = 800;

    void push(Collection<Long> userIds, long postId, long createdEpochMs);

    void remove(Long userId, Collection<Long> postIds);

    /** 最近的条目（score = 发布时间毫秒），新到旧。 */
    List<FeedEntry> recent(Long userId, int limit);
}
