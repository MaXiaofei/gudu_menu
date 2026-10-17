package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.ReactionType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 互动关系仓储：赞/收藏、关注、照着做、举报。所有「加」操作靠唯一键幂等，返回值表示是否真的发生了变化
 * （调用方据此决定是否累加计数，重复提交不重复计数）。
 */
public interface InteractionRepository {

    boolean addReaction(Long postId, Long userId, ReactionType type);

    boolean removeReaction(Long postId, Long userId, ReactionType type);

    Set<Long> reactedPostIds(Long userId, Collection<Long> postIds, ReactionType type);

    boolean follow(Long followerId, Long followeeId);

    boolean unfollow(Long followerId, Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);

    Set<Long> followees(Long followerId);

    List<Long> followers(Long followeeId);

    long followerCount(Long followeeId);

    /** followerId 关注的人里粉丝数超过 threshold 的「大 V」（关注流对他们读扩散）。 */
    Set<Long> bigFollowees(Long followerId, long threshold);

    Optional<Long> copiedDishId(Long postId, Long userId);

    void saveCopy(Long postId, Long userId, Long dishId);

    /** 最近照着做的人（时间倒序）。 */
    List<Long> recentCopiers(Long postId, int limit);

    boolean addReport(Long postId, Long reporterId, String reason);

    long reportCount(Long postId);

    Map<Long, Long> reportCounts(Collection<Long> postIds);

    long reportsSince(LocalDateTime since);

    /** 举报明细（按帖子分组，时间倒序）。 */
    Map<Long, List<Report>> reportsOf(Collection<Long> postIds);

    record Report(Long postId, Long reporterId, String reason, LocalDateTime createdAt) {
    }
}
