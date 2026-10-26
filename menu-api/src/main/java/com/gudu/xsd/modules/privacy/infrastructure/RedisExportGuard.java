package com.gudu.xsd.modules.privacy.infrastructure;

import com.gudu.xsd.modules.privacy.domain.repository.ExportGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;

/**
 * Redis 实现：
 * <ul>
 *   <li>限频 key {@code gudu:export:count:{accountId}:{yyyy-MM-dd}}，过期 2 天；</li>
 *   <li>下载凭证 key {@code gudu:export:token:{token}} → accountId；token = 32 字节随机数 base64url。</li>
 * </ul>
 * 与登录限流不同：Redis 故障时不放行（导出含敏感信息，宁可失败）。
 */
@Component
@RequiredArgsConstructor
public class RedisExportGuard implements ExportGuard {

    private static final String COUNT = "gudu:export:count:";
    private static final String TOKEN = "gudu:export:token:";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Override
    public long incrementToday(Long accountId) {
        String key = COUNT + accountId + ":" + LocalDate.now(clock.withZone(ZONE));
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) redis.expire(key, Duration.ofDays(2));
        return n == null ? 0 : n;
    }

    @Override
    public String issueToken(Long accountId, Duration ttl) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        redis.opsForValue().set(TOKEN + token, String.valueOf(accountId), ttl);
        return token;
    }

    @Override
    public Optional<Long> resolveToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{20,64}")) return Optional.empty();
        String v = redis.opsForValue().get(TOKEN + token);
        try {
            return v == null ? Optional.empty() : Optional.of(Long.parseLong(v));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
