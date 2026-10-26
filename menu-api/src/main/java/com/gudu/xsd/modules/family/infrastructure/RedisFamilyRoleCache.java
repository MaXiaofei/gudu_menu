package com.gudu.xsd.modules.family.infrastructure;

import com.gudu.xsd.modules.family.domain.repository.FamilyRoleCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis hash {@code gudu:family:roles:{familyId}} → accountId → role，整 key TTL 5 分钟。
 * Redis 故障时视为未命中（回源数据库），不影响正确性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFamilyRoleCache implements FamilyRoleCache {

    static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "gudu:family:roles:";

    private final StringRedisTemplate redis;

    @Override
    public Optional<String> get(Long familyId, Long accountId) {
        try {
            Object v = redis.opsForHash().get(PREFIX + familyId, String.valueOf(accountId));
            return Optional.ofNullable(v).map(Object::toString);
        } catch (RuntimeException e) {
            log.debug("家庭角色缓存读取失败，回源: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long familyId, Long accountId, String roleOrNone) {
        try {
            String key = PREFIX + familyId;
            redis.opsForHash().put(key, String.valueOf(accountId), roleOrNone);
            redis.expire(key, TTL);
        } catch (RuntimeException e) {
            log.debug("家庭角色缓存写入失败: {}", e.getMessage());
        }
    }

    @Override
    public void evictFamily(Long familyId) {
        try {
            redis.delete(PREFIX + familyId);
        } catch (RuntimeException e) {
            log.warn("家庭角色缓存失效失败 familyId={}: {}", familyId, e.getMessage());
        }
    }
}
