package com.gudu.xsd.modules.agent.infrastructure;

import com.gudu.xsd.modules.agent.domain.repository.AgentQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 每人每日对话轮数（Redis 计数，48h 过期）；Redis 不可用时放行。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAgentQuota implements AgentQuota {

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Override
    public boolean consume(Long memberId, int limit) {
        if (limit <= 0) return true;
        String key = "agent:quota:" + memberId + ":" + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        try {
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) redis.expire(key, Duration.ofHours(48));
            return n == null || n <= limit;
        } catch (RuntimeException e) {
            log.warn("咕嘟配额计数失败 member={} err={}", memberId, e.getMessage());
            return true;
        }
    }
}
