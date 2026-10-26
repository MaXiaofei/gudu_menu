package com.gudu.xsd.modules.identity.infrastructure;

import com.gudu.xsd.modules.identity.domain.service.LoginThrottle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 计数：key {@code gudu:login:fail:{登录名}}，15 分钟窗口内失败 5 次锁定至窗口结束。
 * Redis 不可用时放行（只记日志），不因限流组件故障导致无法登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLoginThrottle implements LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String PREFIX = "gudu:login:fail:";

    private final StringRedisTemplate redis;

    @Override
    public boolean isLocked(String loginName) {
        try {
            String v = redis.opsForValue().get(PREFIX + loginName);
            return v != null && Integer.parseInt(v) >= MAX_FAILURES;
        } catch (RuntimeException e) {
            log.warn("登录限流读取失败，放行: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void recordFailure(String loginName) {
        try {
            Long n = redis.opsForValue().increment(PREFIX + loginName);
            if (n != null && n == 1L) redis.expire(PREFIX + loginName, WINDOW);
        } catch (RuntimeException e) {
            log.warn("登录限流计数失败: {}", e.getMessage());
        }
    }

    @Override
    public void reset(String loginName) {
        try {
            redis.delete(PREFIX + loginName);
        } catch (RuntimeException e) {
            log.warn("登录限流重置失败: {}", e.getMessage());
        }
    }
}
