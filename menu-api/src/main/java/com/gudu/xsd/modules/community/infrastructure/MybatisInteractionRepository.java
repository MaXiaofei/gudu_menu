package com.gudu.xsd.modules.community.infrastructure;

import com.gudu.xsd.modules.community.domain.model.ReactionType;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityRelationMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityRelationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisInteractionRepository implements InteractionRepository {

    private final CommunityRelationMapper mapper;

    @Override
    public boolean addReaction(Long postId, Long userId, ReactionType type) {
        return mapper.insertReaction(postId, userId, type.name()) > 0;
    }

    @Override
    public boolean removeReaction(Long postId, Long userId, ReactionType type) {
        return mapper.deleteReaction(postId, userId, type.name()) > 0;
    }

    @Override
    public Set<Long> reactedPostIds(Long userId, Collection<Long> postIds, ReactionType type) {
        List<Long> ids = clean(postIds);
        if (userId == null || ids.isEmpty()) return Set.of();
        return new HashSet<>(mapper.reactedPostIds(userId, type.name(), ids));
    }

    @Override
    public boolean follow(Long followerId, Long followeeId) {
        return mapper.insertFollow(followerId, followeeId) > 0;
    }

    @Override
    public boolean unfollow(Long followerId, Long followeeId) {
        return mapper.deleteFollow(followerId, followeeId) > 0;
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) return false;
        return mapper.countFollow(followerId, followeeId) > 0;
    }

    @Override
    public Set<Long> followees(Long followerId) {
        if (followerId == null) return Set.of();
        return new HashSet<>(mapper.followees(followerId));
    }

    @Override
    public List<Long> followers(Long followeeId) {
        return mapper.followers(followeeId);
    }

    @Override
    public long followerCount(Long followeeId) {
        return mapper.followerCount(followeeId);
    }

    @Override
    public Set<Long> bigFollowees(Long followerId, long threshold) {
        if (followerId == null) return Set.of();
        return new HashSet<>(mapper.bigFollowees(followerId, threshold));
    }

    @Override
    public Optional<Long> copiedDishId(Long postId, Long userId) {
        return Optional.ofNullable(mapper.copiedDishId(postId, userId));
    }

    @Override
    public void saveCopy(Long postId, Long userId, Long dishId) {
        mapper.insertCopy(postId, userId, dishId);
    }

    @Override
    public List<Long> recentCopiers(Long postId, int limit) {
        return mapper.recentCopiers(postId, limit);
    }

    @Override
    public boolean addReport(Long postId, Long reporterId, String reason) {
        return mapper.insertReport(postId, reporterId, reason) > 0;
    }

    @Override
    public long reportCount(Long postId) {
        return mapper.reportCount(postId);
    }

    @Override
    public Map<Long, Long> reportCounts(Collection<Long> postIds) {
        List<Long> ids = clean(postIds);
        if (ids.isEmpty()) return Map.of();
        return mapper.reportCounts(ids).stream()
                .collect(Collectors.toMap(CommunityRelationPO::getPostId, CommunityRelationPO::getTargetId, (a, b) -> a));
    }

    @Override
    public long reportsSince(LocalDateTime since) {
        return mapper.reportsSince(since);
    }

    @Override
    public Map<Long, List<Report>> reportsOf(Collection<Long> postIds) {
        List<Long> ids = clean(postIds);
        if (ids.isEmpty()) return Map.of();
        return mapper.reports(ids).stream()
                .map(r -> new Report(r.getPostId(), r.getUserId(), r.getReason(), r.getCreateTime()))
                .collect(Collectors.groupingBy(Report::postId));
    }

    private static List<Long> clean(Collection<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
