package com.gudu.xsd.modules.aiguard.infrastructure;

import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.domain.port.AiUsageCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Redis 次数计数：{@code ai:quota:{类别}:{账号}:{yyyyMMdd}}（每人）与 {@code ai:quota:total:{类别}:{yyyyMMdd}}（全站展示），
 * 48h 过期。任何 Redis 异常统一包成 {@link UsageStoreException}，由 {@code AiQuotaService} 按拒绝处理。
 */
@Component
@RequiredArgsConstructor
public class RedisAiUsageCounter implements AiUsageCounter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration TTL = Duration.ofHours(48);

    private final StringRedisTemplate redis;

    @Override
    public long incrementUser(AiQuotaCategory category, Long accountId, LocalDate day) {
        return increment("ai:quota:" + category.name().toLowerCase() + ":" + accountId + ":" + day.format(DAY));
    }

    @Override
    public void incrementTotal(AiQuotaCategory category, LocalDate day) {
        increment(totalKey(category, day));
    }

    @Override
    public Map<AiQuotaCategory, Long> totals(LocalDate day) {
        Map<AiQuotaCategory, Long> out = new EnumMap<>(AiQuotaCategory.class);
        try {
            for (AiQuotaCategory c : AiQuotaCategory.values()) {
                String v = redis.opsForValue().get(totalKey(c, day));
                out.put(c, v == null ? 0L : Long.parseLong(v));
            }
        } catch (RuntimeException e) {
            throw new UsageStoreException("读取全站次数失败: " + e.getMessage(), e);
        }
        return out;
    }

    private long increment(String key) {
        try {
            Long n = redis.opsForValue().increment(key);
            if (n == null) throw new IllegalStateException("increment 返回空");
            if (n == 1L) redis.expire(key, TTL);
            return n;
        } catch (RuntimeException e) {
            throw new UsageStoreException("Redis 计数失败: " + e.getMessage(), e);
        }
    }

    private static String totalKey(AiQuotaCategory c, LocalDate day) {
        return "ai:quota:total:" + c.name().toLowerCase() + ":" + day.format(DAY);
    }
}
