package com.gudu.xsd.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * {@link AiRateLimited} 切面：Redis 键 {@code ai:limit:{loginId}:{yyyyMMdd}} 自增计数，48h 过期。
 * Redis 不可用时放行并告警（不因限流设施故障阻断主流程）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AiRateLimitAspect {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redis;

    @Value("${gudu.ai.daily-limit:50}")
    private int dailyLimit;

    @Around("@annotation(com.gudu.xsd.common.security.AiRateLimited)")
    public Object limit(ProceedingJoinPoint pjp) throws Throwable {
        if (dailyLimit > 0) {
            Object loginId = StpUtil.getLoginId();
            String key = "ai:limit:" + loginId + ":" + LocalDate.now().format(DAY);
            Long count = null;
            try {
                count = redis.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redis.expire(key, Duration.ofHours(48));
                }
            } catch (RuntimeException e) {
                log.warn("AI 限流计数失败 loginId={} err={}", loginId, e.getMessage());
            }
            if (count != null && count > dailyLimit) {
                throw BizException.tooManyRequests("今日 AI 调用已达上限（" + dailyLimit + " 次），明天再试");
            }
        }
        return pjp.proceed();
    }
}
