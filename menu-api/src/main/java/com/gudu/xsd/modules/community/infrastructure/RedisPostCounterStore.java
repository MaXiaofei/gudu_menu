package com.gudu.xsd.modules.community.infrastructure;

import com.gudu.xsd.modules.community.domain.model.CounterDelta;
import com.gudu.xsd.modules.community.domain.model.PostCounter;
import com.gudu.xsd.modules.community.domain.repository.PostCounterStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 计数增量：Hash {@code gudu:community:cnt:{postId}}（字段 LIKE/COLLECT/COMMENT/COPY）+ 脏集合。
 * 回刷时先 RENAME 成临时 key 再读删——RENAME 原子，之后的新增量落到新 key 并重新入脏集合，不会丢也不会重复。
 */
@Component
@RequiredArgsConstructor
public class RedisPostCounterStore implements PostCounterStore {

    static final String KEY = "gudu:community:cnt:";
    static final String DIRTY = "gudu:community:cnt:dirty";

    private final StringRedisTemplate redis;

    @Override
    public void incr(Long postId, PostCounter counter, long delta) {
        if (postId == null || delta == 0) return;
        redis.opsForHash().increment(KEY + postId, counter.name(), delta);
        redis.opsForSet().add(DIRTY, String.valueOf(postId));
    }

    @Override
    public Map<Long, CounterDelta> pending(Collection<Long> postIds) {
        Map<Long, CounterDelta> out = new HashMap<>();
        if (postIds == null) return out;
        for (Long id : postIds) {
            if (id == null) continue;
            CounterDelta d = toDelta(redis.opsForHash().entries(KEY + id));
            if (!d.isZero()) out.put(id, d);
        }
        return out;
    }

    @Override
    public Map<Long, CounterDelta> drain(int max) {
        Map<Long, CounterDelta> out = new HashMap<>();
        List<String> ids = redis.opsForSet().pop(DIRTY, max);
        if (ids == null) return out;
        for (String id : ids) {
            String key = KEY + id;
            if (!Boolean.TRUE.equals(redis.hasKey(key))) continue;
            String tmp = key + ":flush:" + UUID.randomUUID();
            try {
                redis.rename(key, tmp);
            } catch (RuntimeException e) {
                continue; // key 在 hasKey 与 rename 之间被取走（并发回刷）
            }
            CounterDelta d = toDelta(redis.opsForHash().entries(tmp));
            redis.delete(tmp);
            if (!d.isZero()) out.merge(Long.parseLong(id), d, RedisPostCounterStore::sum);
        }
        return out;
    }

    private static CounterDelta toDelta(Map<Object, Object> hash) {
        CounterDelta d = CounterDelta.NONE;
        if (hash == null) return d;
        for (Map.Entry<Object, Object> e : hash.entrySet()) {
            try {
                d = d.plus(PostCounter.valueOf(String.valueOf(e.getKey())), Long.parseLong(String.valueOf(e.getValue())));
            } catch (IllegalArgumentException ignored) {
                // 未知字段 / 非数字：忽略
            }
        }
        return d;
    }

    private static CounterDelta sum(CounterDelta a, CounterDelta b) {
        return new CounterDelta(a.likes() + b.likes(), a.collects() + b.collects(), a.comments() + b.comments(),
                a.copies() + b.copies());
    }
}
