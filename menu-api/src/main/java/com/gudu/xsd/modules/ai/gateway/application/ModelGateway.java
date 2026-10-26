package com.gudu.xsd.modules.ai.gateway.application;

import com.gudu.xsd.modules.ai.gateway.domain.event.GatewayConfigChangedEvent;
import com.gudu.xsd.modules.ai.gateway.domain.model.CallAttempt;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.GatewayCallRecord;
import com.gudu.xsd.modules.ai.gateway.domain.model.KeySource;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelSpec;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;
import com.gudu.xsd.modules.ai.gateway.domain.model.RuleResponder;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.model.TokenUsage;
import com.gudu.xsd.modules.ai.gateway.domain.port.EnvKeyLookup;
import com.gudu.xsd.modules.ai.gateway.domain.port.ImageInliner;
import com.gudu.xsd.modules.ai.gateway.domain.port.ModelTransport;
import com.gudu.xsd.modules.ai.gateway.domain.port.SecretCipher;
import com.gudu.xsd.modules.ai.gateway.domain.port.TransportException;
import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayCallLogRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelProviderRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelRouteRepository;
import com.gudu.xsd.modules.ai.gateway.domain.service.CostCalculator;
import com.gudu.xsd.modules.ai.gateway.domain.service.KeyMasker;
import com.gudu.xsd.modules.ai.gateway.domain.service.Pseudonymizer;
import com.gudu.xsd.modules.ai.gateway.domain.service.RouteChainResolver;
import com.gudu.xsd.modules.ai.gateway.domain.service.RouteChainResolver.Candidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型网关：所有模型调用的唯一出口。按能力（CHAT / TOOLS / VISION）取路由 → 解析可执行目标链 →
 * 逐个目标调用（同一目标按 maxRetries 重试可重试错误）→ 全部失败落到调用方的规则兜底。
 * 每次尝试记录供应商 / 模型 / 耗时 / tokens / 成本（按调用时刻的高峰价）。
 *
 * <p>配置来自库表（V73），本地缓存 {@link #CACHE_TTL}；后台修改后经 Redis 广播
 * {@link GatewayConfigChangedEvent} 立即失效。库表不可读时使用 {@link GatewayDefaults}。
 */
@Slf4j
@Service
public class ModelGateway {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String RULE = "mock";

    private final ModelProviderRepository providerRepository;
    private final ModelRouteRepository routeRepository;
    private final GatewayCallLogRepository callLogRepository;
    private final ModelTransport transport;
    private final SecretCipher cipher;
    private final EnvKeyLookup envKeys;
    private final ImageInliner imageInliner;
    private final Clock clock;

    private volatile Snapshot snapshot;

    public ModelGateway(ModelProviderRepository providerRepository, ModelRouteRepository routeRepository,
                        GatewayCallLogRepository callLogRepository, ModelTransport transport, SecretCipher cipher,
                        EnvKeyLookup envKeys, ImageInliner imageInliner, Clock clock) {
        this.providerRepository = providerRepository;
        this.routeRepository = routeRepository;
        this.callLogRepository = callLogRepository;
        this.transport = transport;
        this.cipher = cipher;
        this.envKeys = envKeys;
        this.imageInliner = imageInliner;
        this.clock = clock;
    }

    /** 当前配置快照（供应商按 id、路由按能力）。 */
    record Snapshot(Map<Long, ModelProvider> providers, Map<Capability, ModelRoute> routes, Instant loadedAt,
                    boolean fromDefaults) {
    }

    // ===================================================================== 调用

    /**
     * 通用调用：按 request.capability 路由。出站前统一做账号标识脱敏（手机号 / openid / wx_ 占位手机号，
     * 见 {@link Pseudonymizer#redactIdentifiers}）——所有调用点的兜底；成员称呼假名化由持有家庭上下文的调用方
     * 用 {@link Pseudonymizer#of} 完成。
     */
    public ChatResult chat(ChatRequest original) {
        ChatRequest request = Pseudonymizer.NONE.mask(original);
        Snapshot s = snapshot();
        ModelRoute route = s.routes().get(request.capability());
        if (route == null) {
            route = GatewayDefaults.routes().stream()
                    .filter(r -> r.capability() == request.capability()).findFirst().orElseThrow();
        }
        long started = System.currentTimeMillis();
        List<CallAttempt> attempts = new ArrayList<>();
        if (request.forceRule()) {
            return runRule(request, attempts, started, true);
        }
        RouteChainResolver.Resolution resolution = RouteChainResolver.resolve(route, s.providers(), this::hasKey);
        RouteTarget primary = route.primary();
        int index = 0;
        for (Candidate c : resolution.candidates()) {
            boolean isPrimary = index == 0 && c.provider() != null && primary.providerId().equals(c.provider().id())
                    && primary.model().equals(c.modelName());
            index++;
            if (c.isRule()) {
                return runRule(request, attempts, started, !isPrimary);
            }
            Optional<ModelReply> reply = tryCandidate(c, route, request, attempts);
            if (reply.isPresent()) {
                return new ChatResult(reply.get(), c.providerName(), c.modelName(), !isPrimary,
                        System.currentTimeMillis() - started, totalCost(attempts), attempts);
            }
        }
        throw new ModelUnavailableException("模型暂不可用");
    }

    /** 文本补全（CHAT）：真实模型全部失败时抛 {@link ModelUnavailableException}，调用方自行兜底。 */
    public String chatText(String scene, String system, String user) {
        return chat(ChatRequest.text(scene, system, user, false)).content();
    }

    /** JSON 输出（CHAT + response_format=json_object）。 */
    public ChatResult chatJson(String scene, String system, String user) {
        return chat(ChatRequest.text(scene, system, user, true));
    }

    /**
     * 视觉（VISION）：图片 + 提示词，要求按 jsonSchema 输出 JSON。
     *
     * @param image      公网 URL 或 data URL（base64）
     * @param jsonSchema 期望输出的 JSON Schema 文本（拼进系统提示，DeepSeek 用 json_object 模式）
     */
    public ChatResult vision(String scene, String image, String prompt, String jsonSchema,
                             RuleResponder ruleFallback) {
        String system = "你是图像识别助手。只输出符合下面 JSON Schema 的 JSON，不要任何解释。\nJSON Schema:\n"
                + (jsonSchema == null ? "{}" : jsonSchema);
        ChatRequest req = new ChatRequest(Capability.VISION, scene,
                List.of(ChatMessage.system(system), ChatMessage.userWithImage(prompt, image)),
                List.of(), true, null, null, ruleFallback, false);
        return chat(req);
    }

    // ===================================================================== 查询（兼容旧接口 / 后台）

    /** 旧 GET /ai/provider：CHAT 主目标供应商名（小写）。 */
    public String legacyProvider() {
        ModelRoute r = snapshot().routes().get(Capability.CHAT);
        if (r == null) return "deepseek";
        ModelProvider p = snapshot().providers().get(r.primary().providerId());
        return p == null ? "deepseek" : p.name().toLowerCase();
    }

    /** 供应商是否拿得到 key（Mock 不需要）。 */
    public boolean hasKey(ModelProvider p) {
        if (p.isMock()) return true;
        try {
            String k = resolveKey(p);
            return k != null && !k.isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 明文 key：只给传输层与连通性测试用，禁止记录 / 返回。 */
    String resolveKey(ModelProvider p) {
        if (p.keySource() == KeySource.DB) {
            if (!cipher.available() || p.encryptedKey() == null) return null;
            return cipher.decrypt(p.encryptedKey());
        }
        if (p.keySource() == KeySource.ENV && p.keyEnv() != null) {
            return envKeys.get(p.keyEnv());
        }
        return null;
    }

    /** 当前快照的只读视图（后台展示用）。 */
    public Map<Long, ModelProvider> providers() {
        return snapshot().providers();
    }

    public Map<Capability, ModelRoute> routes() {
        return snapshot().routes();
    }

    // ===================================================================== 缓存

    /** 收到配置变更（本实例或 Redis 广播）：失效缓存，下次调用重新加载。 */
    @EventListener
    public void onConfigChanged(GatewayConfigChangedEvent event) {
        log.info("模型网关配置变更（{}），失效本地缓存", event.reason());
        invalidate();
    }

    public void invalidate() {
        this.snapshot = null;
    }

    Snapshot snapshot() {
        Snapshot s = this.snapshot;
        Instant now = clock.instant();
        if (s != null && Duration.between(s.loadedAt(), now).compareTo(CACHE_TTL) < 0) {
            return s;
        }
        s = load(now);
        this.snapshot = s;
        return s;
    }

    private Snapshot load(Instant now) {
        try {
            List<ModelProvider> providers = providerRepository.findAll();
            List<ModelRoute> routes = routeRepository.findAll();
            if (!providers.isEmpty() && !routes.isEmpty()) {
                return new Snapshot(index(providers), routesByCap(routes), now, false);
            }
            log.warn("模型网关库表为空，使用内置默认配置");
        } catch (RuntimeException e) {
            log.warn("读取模型网关配置失败，使用内置默认配置: {}", e.getMessage());
        }
        return new Snapshot(index(GatewayDefaults.providers()), routesByCap(GatewayDefaults.routes()), now, true);
    }

    private static Map<Long, ModelProvider> index(List<ModelProvider> list) {
        return list.stream().collect(Collectors.toMap(ModelProvider::id, Function.identity(), (a, b) -> a,
                LinkedHashMap::new));
    }

    private static Map<Capability, ModelRoute> routesByCap(List<ModelRoute> list) {
        return list.stream().collect(Collectors.toMap(ModelRoute::capability, Function.identity(), (a, b) -> a,
                LinkedHashMap::new));
    }

    // ===================================================================== 内部

    private Optional<ModelReply> tryCandidate(Candidate c, ModelRoute route, ChatRequest request,
                                              List<CallAttempt> attempts) {
        ModelProvider p = c.provider();
        ModelSpec spec = c.spec();
        String key = resolveKey(p);
        ModelTransport.Endpoint ep = new ModelTransport.Endpoint(p.name(), p.baseUrl(), spec.model(),
                spec.supportsThinking());
        ThinkingMode wanted = request.thinking() != null ? request.thinking() : route.thinking();
        ThinkingMode thinking = spec.supportsThinking() ? wanted : ThinkingMode.DISABLED;
        ChatRequest current = request;
        boolean inlined = false;
        int tries = 1 + route.maxRetries();
        for (int i = 0; i < tries; i++) {
            long t0 = System.currentTimeMillis();
            Instant at = clock.instant();
            try {
                ModelReply reply = transport.call(ep, key, current, thinking, route.timeoutMs());
                long ms = System.currentTimeMillis() - t0;
                BigDecimal cost = CostCalculator.costFen(spec.price(), spec.peakHours(), reply.usage(), at);
                record(request, p.name(), spec.model(), true, null, ms, reply.usage(), cost, attempts);
                return Optional.of(reply);
            } catch (TransportException e) {
                long ms = System.currentTimeMillis() - t0;
                String err = KeyMasker.scrub(e.getMessage(), key);
                record(request, p.name(), spec.model(), false, err, ms, TokenUsage.ZERO, BigDecimal.ZERO, attempts);
                if (e.imageFetchFailed() && !inlined && request.capability() == Capability.VISION) {
                    Optional<ChatRequest> withData = inlineImages(current);
                    if (withData.isPresent()) {
                        current = withData.get();
                        inlined = true;
                        i--; // 换 base64 重发不占重试次数
                        continue;
                    }
                }
                if (!e.retryable()) break;
            } catch (RuntimeException e) {
                long ms = System.currentTimeMillis() - t0;
                record(request, p.name(), spec.model(), false, KeyMasker.scrub(String.valueOf(e.getMessage()), key),
                        ms, TokenUsage.ZERO, BigDecimal.ZERO, attempts);
                break;
            }
        }
        return Optional.empty();
    }

    private ChatResult runRule(ChatRequest request, List<CallAttempt> attempts, long started, boolean degraded) {
        if (request.ruleFallback() == null) {
            throw new ModelUnavailableException("模型暂不可用（" + attempts.size() + " 次尝试均失败）");
        }
        long t0 = System.currentTimeMillis();
        ModelReply reply = request.ruleFallback().reply(request);
        record(request, RULE, RULE, true, null, System.currentTimeMillis() - t0, TokenUsage.ZERO, BigDecimal.ZERO,
                attempts);
        return new ChatResult(reply, RULE, RULE, degraded, System.currentTimeMillis() - started,
                totalCost(attempts), attempts);
    }

    private Optional<ChatRequest> inlineImages(ChatRequest req) {
        List<ChatMessage> out = new ArrayList<>(req.messages().size());
        boolean changed = false;
        for (ChatMessage m : req.messages()) {
            String img = m.imageUrl();
            if (img != null && (img.startsWith("http://") || img.startsWith("https://"))) {
                try {
                    out.add(m.withImage(imageInliner.toDataUrl(img)));
                    changed = true;
                    continue;
                } catch (RuntimeException e) {
                    log.warn("视觉图片内联失败: {}", e.getMessage());
                    return Optional.empty();
                }
            }
            out.add(m);
        }
        return changed ? Optional.of(req.withMessages(out)) : Optional.empty();
    }

    private void record(ChatRequest req, String provider, String model, boolean ok, String error, long ms,
                        TokenUsage usage, BigDecimal cost, List<CallAttempt> attempts) {
        attempts.add(new CallAttempt(provider, model, ok, error, ms, usage, cost));
        try {
            callLogRepository.append(new GatewayCallRecord(req.capability(), req.scene(), provider, model, ok, error,
                    ms, usage, cost, req.traceId(), req.memberId(), LocalDateTime.now(clock)));
        } catch (RuntimeException e) {
            log.warn("网关调用日志写入失败 scene={} err={}", req.scene(), e.getMessage());
        }
    }

    private static BigDecimal totalCost(List<CallAttempt> attempts) {
        return attempts.stream().map(CallAttempt::costFen).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
