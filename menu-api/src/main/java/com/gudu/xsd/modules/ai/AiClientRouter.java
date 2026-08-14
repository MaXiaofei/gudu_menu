package com.gudu.xsd.modules.ai;

import com.gudu.xsd.modules.ai.dto.DishEstimateRequest;
import com.gudu.xsd.modules.ai.dto.DishEstimateResponse;
import com.gudu.xsd.modules.ai.dto.MenuCandidate;
import com.gudu.xsd.modules.ai.dto.MenuRecommendRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI client 运行时路由器：常驻 {@code @Primary} bean，{@code AiService} 注入本类。
 *
 * <p>把三个常驻实现按「当前 provider 选择」委托：
 * <ul>
 *   <li>{@code DeepSeekAiClient}（真调 DeepSeek，失败降级 mock）</li>
 *   <li>{@code GlmAiClient}（真调 智谱 GLM，失败降级 mock）</li>
 *   <li>{@code MockAiClient}（规则表兜底，无需 key）</li>
 * </ul>
 *
 * <p>选择状态来源（带持久化，重启不丢）：
 * <ol>
 *   <li>优先读 Redis key {@link #REDIS_KEY}（运行时 {@code PUT /ai/provider} 写入）。</li>
 *   <li>Redis 无值则回退启动配置 {@code gudu.ai.provider}（env {@code GUDU_AI_PROVIDER}，默认 deepseek）。</li>
 * </ol>
 *
 * <p>切换通过 {@link #switchProvider(String)}：校验合法值后写 Redis，立即生效，重启后仍保持。
 * Redis 不可用（连接抖动等）不阻断主流程：读降级用启动初值，写降级抛错提示重试。
 */
@Slf4j
@Primary
@Component
public class AiClientRouter implements AiClient {

    /** Redis key：当前 AI provider（deepseek/glm/mock）。 */
    static final String REDIS_KEY = "gudu:ai:provider";

    /** 合法 provider 值（顺序即后台展示顺序）。 */
    public static final List<String> PROVIDERS = List.of("deepseek", "glm", "mock");

    private final Map<String, AiClient> clients;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DeepSeekAccessor deepSeekAccessor;
    private final GlmAccessor glmAccessor;

    /** 启动初值：配置 {@code gudu.ai.provider}，运行时仅当 Redis 无值时回退用。 */
    private final String bootProvider;

    public AiClientRouter(
            com.gudu.xsd.modules.ai.impl.DeepSeekAiClient deepSeek,
            com.gudu.xsd.modules.ai.impl.GlmAiClient glm,
            com.gudu.xsd.modules.ai.impl.MockAiClient mock,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${gudu.ai.provider:deepseek}") String bootProvider) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.bootProvider = normalize(bootProvider, "deepseek");
        this.clients = Map.of(
                "deepseek", deepSeek,
                "glm", glm,
                "mock", mock);
        // 用包内可见的访问器读 key 是否已配，避免反射；null-safe。
        this.deepSeekAccessor = new DeepSeekAccessor(deepSeek);
        this.glmAccessor = new GlmAccessor(glm);
    }

    /** 取当前 provider：Redis 有值优先，否则回退启动初值。 */
    public String currentProvider() {
        try {
            String v = redis.opsForValue().get(REDIS_KEY);
            if (v != null && !v.isBlank()) {
                String p = v.trim();
                if (clients.containsKey(p)) return p;
                // Redis 里是脏值（历史误写）→ 清掉回退，下次写覆盖即可
                log.warn("Redis {} 脏值 [{}]，回退启动初值 {}", REDIS_KEY, p, bootProvider);
            }
        } catch (RuntimeException e) {
            log.warn("读 Redis provider 失败，回退启动初值 {}: {}", bootProvider, e.getMessage());
        }
        return bootProvider;
    }

    /**
     * 运行时切换 provider。校验合法值后写 Redis，立即生效。
     *
     * @throws com.gudu.xsd.common.BizException 非法值 或 Redis 写入失败
     */
    public String switchProvider(String provider) {
        if (provider == null || !clients.containsKey(provider)) {
            throw new com.gudu.xsd.common.BizException(
                    "非法 provider：" + provider + "，可选 " + PROVIDERS);
        }
        try {
            redis.opsForValue().set(REDIS_KEY, provider);
        } catch (RuntimeException e) {
            throw new com.gudu.xsd.common.BizException("切换失败：Redis 不可用，请重试 (" + e.getMessage() + ")");
        }
        log.info("AI provider 已切换至 {}", provider);
        return provider;
    }

    /** 当前选中 provider 的 client（Redis/启动初值综合后，必落在合法 client）。 */
    private AiClient currentClient() {
        return clients.get(currentProvider());
    }

    @Override
    public NutritionFillResponse fillNutrition(NutritionFillRequest req) {
        return currentClient().fillNutrition(req);
    }

    @Override
    public DishEstimateResponse estimateDish(DishEstimateRequest req) {
        return currentClient().estimateDish(req);
    }

    /** 各 provider 是否已配 key（mock 永远可用）；用于 GET /ai/provider 展示可用状态。 */
    public boolean providerReady(String provider) {
        return switch (provider) {
            case "deepseek" -> deepSeekAccessor.hasKey();
            case "glm" -> glmAccessor.hasKey();
            case "mock" -> true;
            default -> false;
        };
    }

    // ---------------- 包内可见访问器：读子类 key 是否已配，避免反射 ----------------

    private static String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String p = raw.trim();
        return "deepseek".equals(p) || "glm".equals(p) || "mock".equals(p) ? p : fallback;
    }

    private record DeepSeekAccessor(com.gudu.xsd.modules.ai.impl.DeepSeekAiClient c) {
        boolean hasKey() { return c != null && c.getKey() != null && !c.getKey().isBlank(); }
    }

    private record GlmAccessor(com.gudu.xsd.modules.ai.impl.GlmAiClient c) {
        boolean hasKey() { return c != null && c.getKey() != null && !c.getKey().isBlank(); }
    }
}
