package com.gudu.xsd.modules.aiguard;

import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.domain.port.AiUsageCounter;
import com.gudu.xsd.modules.aiguard.infrastructure.RedisAiUsageCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis 计数：键含类别/账号/日期；首次计数设 48h 过期；任何 Redis 异常统一成存储故障。 */
class RedisAiUsageCounterTest {

    StringRedisTemplate redis;
    ValueOperations<String, String> ops;
    RedisAiUsageCounter counter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        counter = new RedisAiUsageCounter(redis);
    }

    @Test
    void 键含类别账号日期_首次计数设过期() {
        when(ops.increment("ai:quota:vision:7:20261102")).thenReturn(1L);
        assertThat(counter.incrementUser(AiQuotaCategory.VISION, 7L, LocalDate.of(2026, 11, 2))).isEqualTo(1L);
        verify(redis).expire(eq("ai:quota:vision:7:20261102"), any());
    }

    @Test
    void Redis异常_包成存储故障() {
        when(ops.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThatThrownBy(() -> counter.incrementUser(AiQuotaCategory.OTHER, 7L, LocalDate.of(2026, 11, 2)))
                .isInstanceOf(AiUsageCounter.UsageStoreException.class);
    }

    @Test
    void 读全站次数失败_也是存储故障() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThatThrownBy(() -> counter.totals(LocalDate.of(2026, 11, 2)))
                .isInstanceOf(AiUsageCounter.UsageStoreException.class);
    }
}
