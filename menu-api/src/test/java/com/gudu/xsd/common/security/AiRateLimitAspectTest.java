package com.gudu.xsd.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** AI 限流切面：按登录账号 + 类别向闸门计数；闸门拒绝（429 / Redis 故障 503）则不执行方法。 */
class AiRateLimitAspectTest {

    private AiQuotaGate gate;
    private AiRateLimitAspect aspect;
    private ProceedingJoinPoint pjp;

    @AiRateLimited(AiQuotaCategory.VISION)
    void vision() {
    }

    @AiRateLimited
    void other() {
    }

    private static AiRateLimited ann(String method) throws Exception {
        return AiRateLimitAspectTest.class.getDeclaredMethod(method).getAnnotation(AiRateLimited.class);
    }

    @BeforeEach
    void setUp() throws Throwable {
        gate = mock(AiQuotaGate.class);
        aspect = new AiRateLimitAspect(gate);
        pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");
    }

    @Test
    void 放行_按登录账号和注解类别计数() throws Throwable {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            assertThat(aspect.limit(pjp, ann("vision"))).isEqualTo("OK");
            assertThat(aspect.limit(pjp, ann("other"))).isEqualTo("OK");
            verify(gate).require(AiQuotaCategory.VISION, 7L);
            verify(gate).require(AiQuotaCategory.OTHER, 7L);
        }
    }

    @Test
    void 超限_429且不执行() throws Throwable {
        doThrow(BizException.tooManyRequests("今日 AI 调用已达上限（50 次），明天再试"))
                .when(gate).require(AiQuotaCategory.OTHER, 7L);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            assertThatThrownBy(() -> aspect.limit(pjp, ann("other")))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode()).isEqualTo(429);
            verify(pjp, never()).proceed();
        }
    }

    @Test
    void Redis故障_闸门拒绝_不放行() throws Throwable {
        doThrow(new BizException(503, "AI 服务暂时不可用，请稍后再试")).when(gate).require(any(), any());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            assertThatThrownBy(() -> aspect.limit(pjp, ann("other"))).isInstanceOf(BizException.class);
            verify(pjp, never()).proceed();
        }
    }
}
