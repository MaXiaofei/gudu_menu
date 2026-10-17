package com.gudu.xsd.modules.community.infrastructure;

import com.gudu.xsd.modules.community.domain.model.FeedEntry;
import com.gudu.xsd.modules.community.domain.repository.FollowInbox;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 关注流收件箱：每用户一个 ZSet（member = postId，score = 发布时间毫秒），截断到最近 {@link #CAPACITY} 条。 */
@Component
@RequiredArgsConstructor
public class RedisFollowInbox implements FollowInbox {

    static final String KEY = "gudu:community:inbox:";

    private final StringRedisTemplate redis;

    @Override
    public void push(Collection<Long> userIds, long postId, long createdEpochMs) {
        if (userIds == null || userIds.isEmpty()) return;
        String member = String.valueOf(postId);
        for (Long uid : userIds) {
            if (uid == null) continue;
            String key = KEY + uid;
            redis.opsForZSet().add(key, member, createdEpochMs);
            redis.opsForZSet().removeRange(key, 0, -(CAPACITY + 1L));
        }
    }

    @Override
    public void remove(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) return;
        redis.opsForZSet().remove(KEY + userId, postIds.stream().map(String::valueOf).toArray());
    }

    @Override
    public List<FeedEntry> recent(Long userId, int limit) {
        if (userId == null) return List.of();
        Set<ZSetOperations.TypedTuple<String>> rows = redis.opsForZSet()
                .reverseRangeWithScores(KEY + userId, 0, Math.max(0, limit - 1L));
        List<FeedEntry> out = new ArrayList<>();
        if (rows == null) return out;
        for (ZSetOperations.TypedTuple<String> t : rows) {
            if (t.getValue() == null || t.getScore() == null) continue;
            long ms = t.getScore().longValue();
            out.add(new FeedEntry(Long.parseLong(t.getValue()), ms, ms));
        }
        return out;
    }
}
