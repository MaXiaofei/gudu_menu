package com.gudu.xsd.common.security;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link AiRateLimited} 切面：按登录账号 + 类别向 {@link AiQuotaGate} 计一次。
 * 超限 429；Redis 故障拒绝（fail-closed，不再放行）。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AiRateLimitAspect {

    private final AiQuotaGate gate;

    @Around("@annotation(limited)")
    public Object limit(ProceedingJoinPoint pjp, AiRateLimited limited) throws Throwable {
        gate.require(limited.value(), StpUtil.getLoginIdAsLong());
        return pjp.proceed();
    }
}
