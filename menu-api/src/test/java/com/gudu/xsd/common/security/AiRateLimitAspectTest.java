package com.gudu.xsd.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** AI 限流：按登录账号计数，超限 429；Redis 故障放行。 */
class AiRateLimitAspectTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private AiRateLimitAspect aspect;
    private ProceedingJoinPoint pjp;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Throwable {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        aspect = new AiRateLimitAspect(redis);
        ReflectionTestUtils.setField(aspect, "dailyLimit", 50);
        pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");
    }

    @Test
    void 未超限_放行_计数键含登录账号() throws Throwable {
        when(ops.increment(anyString())).thenReturn(1L);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginId).thenReturn(7L);
            assertThat(aspect.limit(pjp)).isEqualTo("OK");
            verify(ops).increment(startsWith("ai:limit:7:"));
            verify(redis).expire(startsWith("ai:limit:7:"), any());
        }
    }

    @Test
    void 超限_429且不执行() throws Throwable {
        when(ops.increment(anyString())).thenReturn(51L);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginId).thenReturn(7L);
            assertThatThrownBy(() -> aspect.limit(pjp))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(429);
            verify(pjp, never()).proceed();
        }
    }

    @Test
    void Redis故障_放行() throws Throwable {
        when(ops.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginId).thenReturn(7L);
            assertThat(aspect.limit(pjp)).isEqualTo("OK");
        }
    }
}
