package com.gudu.xsd.modules.ai.gateway.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.GatewayView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.HealthView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ModelView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.PeakHoursView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.PriceView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderSaveCommand;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderTestResult;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.RouteSaveCommand;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.RouteView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.TargetView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.UsageRow;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.UsageView;
import com.gudu.xsd.modules.ai.gateway.domain.event.GatewayConfigChangedEvent;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.GatewayCallRecord;
import com.gudu.xsd.modules.ai.gateway.domain.model.KeySource;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelPrice;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelSpec;
import com.gudu.xsd.modules.ai.gateway.domain.model.PeakHours;
import com.gudu.xsd.modules.ai.gateway.domain.model.ProviderType;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.port.GatewayChangeBus;
import com.gudu.xsd.modules.ai.gateway.domain.port.ModelTransport;
import com.gudu.xsd.modules.ai.gateway.domain.port.SecretCipher;
import com.gudu.xsd.modules.ai.gateway.domain.port.TransportException;
import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayAuditRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayCallLogRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelProviderRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelRouteRepository;
import com.gudu.xsd.modules.ai.gateway.domain.service.KeyMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型网关后台用例：供应商增删改与连通性测试、能力路由配置、用量统计。
 * 每次变更写审计并广播失效（本实例立即失效 + Redis 通知其他实例）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayAdminAppService {

    /** 视觉连通性测试用的 1×1 PNG。 */
    static final String PROBE_IMAGE = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    static final int TEST_TIMEOUT_MS = 20_000;

    private final ModelProviderRepository providerRepository;
    private final ModelRouteRepository routeRepository;
    private final GatewayAuditRepository auditRepository;
    private final GatewayCallLogRepository callLogRepository;
    private final ModelGateway gateway;
    private final ModelTransport transport;
    private final SecretCipher cipher;
    private final GatewayChangeBus changeBus;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ===================================================================== 查询

    public GatewayView overview() {
        List<ProviderView> providers = gateway.providers().values().stream().map(this::toView).toList();
        List<RouteView> routes = gateway.routes().values().stream()
                .sorted(Comparator.comparing(ModelRoute::capability)).map(GatewayAdminAppService::toView).toList();
        return new GatewayView(providers, routes);
    }

    // ===================================================================== 供应商

    @Transactional(rollbackFor = Exception.class)
    public ProviderView createProvider(ProviderSaveCommand cmd, String operator) {
        if (cmd == null) throw new BizException("参数不能为空");
        providerRepository.findByName(safe(cmd.name())).ifPresent(p -> {
            throw new BizException(409, "供应商名称已存在：" + p.name());
        });
        String enc = encryptKey(cmd.apiKey());
        ProviderType type = cmd.type() == null || cmd.type().isBlank()
                ? ProviderType.OPENAI_COMPATIBLE : ProviderType.valueOf(cmd.type().trim().toUpperCase());
        ModelProvider p = ModelProvider.create(cmd.name(), type, cmd.baseUrl(), enc,
                cmd.enabled() == null || cmd.enabled(), toSpecs(cmd.models()));
        providerRepository.save(p);
        changed(operator, "PROVIDER_CREATE", "provider:" + p.id(), describe(p));
        return toView(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProviderView updateProvider(Long id, ProviderSaveCommand cmd, String operator) {
        if (cmd == null) throw new BizException("参数不能为空");
        ModelProvider p = providerRepository.findById(id).orElseThrow(() -> new BizException(404, "供应商不存在"));
        if (cmd.name() != null && !cmd.name().isBlank() && !cmd.name().trim().equals(p.name())) {
            providerRepository.findByName(cmd.name().trim()).ifPresent(o -> {
                throw new BizException(409, "供应商名称已存在：" + o.name());
            });
            p.rename(cmd.name());
        }
        if (cmd.baseUrl() != null) p.changeEndpoint(cmd.baseUrl());
        if (cmd.models() != null) {
            List<ModelSpec> specs = toSpecs(cmd.models());
            assertModelsStillRouted(p.id(), specs);
            p.replaceModels(specs);
        }
        if (cmd.enabled() != null) p.enable(cmd.enabled());
        p.rotateKey(encryptKey(cmd.apiKey()));
        providerRepository.save(p);
        changed(operator, "PROVIDER_UPDATE", "provider:" + p.id(), describe(p));
        return toView(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProvider(Long id, String operator) {
        ModelProvider p = providerRepository.findById(id).orElseThrow(() -> new BizException(404, "供应商不存在"));
        List<String> usedBy = routeRepository.findAll().stream().filter(r -> r.references(id))
                .map(r -> r.capability().name()).toList();
        if (!usedBy.isEmpty()) {
            throw new BizException(409, "供应商被路由引用（" + String.join("、", usedBy) + "），请先调整路由");
        }
        providerRepository.delete(id);
        changed(operator, "PROVIDER_DELETE", "provider:" + id, describe(p));
    }

    /** 连通性测试：真实发一次很小的请求（不走降级链），记录健康状态。 */
    @Transactional(rollbackFor = Exception.class)
    public ProviderTestResult testProvider(Long id, String model, String capability, String operator) {
        ModelProvider p = providerRepository.findById(id).orElseThrow(() -> new BizException(404, "供应商不存在"));
        Capability cap = capability == null || capability.isBlank() ? Capability.CHAT : Capability.of(capability);
        ModelSpec spec = (model == null || model.isBlank() ? p.firstModelFor(cap) : p.model(model))
                .orElseThrow(() -> new BizException("模型不存在或不支持 " + cap));
        if (p.isMock()) {
            return new ProviderTestResult(true, 0, null, "规则兜底，无需网络");
        }
        String key = gateway.resolveKey(p);
        if (key == null || key.isBlank()) {
            return new ProviderTestResult(false, 0, "未配置 key", null);
        }
        ChatRequest probe = cap == Capability.VISION
                ? new ChatRequest(cap, "gateway_test", List.of(ChatMessage.userWithImage(
                "这张图片是什么颜色？只回答一个词。", PROBE_IMAGE)), List.of(), false, null, null, null, false)
                : new ChatRequest(cap, "gateway_test", List.of(ChatMessage.user("请只回复：ok")), List.of(), false,
                null, null, null, false);
        long t0 = System.currentTimeMillis();
        ProviderTestResult result;
        try {
            ModelReply reply = transport.call(new ModelTransport.Endpoint(p.name(), p.baseUrl(), spec.model(),
                    spec.supportsThinking()), key, probe, ThinkingMode.DISABLED, TEST_TIMEOUT_MS);
            String sample = reply.content() == null ? "" : reply.content().trim();
            result = new ProviderTestResult(true, System.currentTimeMillis() - t0, null,
                    sample.length() > 80 ? sample.substring(0, 80) : sample);
        } catch (TransportException | IllegalStateException e) {
            result = new ProviderTestResult(false, System.currentTimeMillis() - t0,
                    KeyMasker.scrub(e.getMessage(), key), null);
        }
        p.recordHealth(result.ok(), (int) result.latencyMs(), LocalDateTime.now(clock));
        providerRepository.save(p);
        auditRepository.append(operator, "PROVIDER_TEST", "provider:" + id,
                json(Map.of("model", spec.model(), "capability", cap.name(), "ok", result.ok())));
        gateway.invalidate();
        return result;
    }

    // ===================================================================== 路由

    @Transactional(rollbackFor = Exception.class)
    public RouteView updateRoute(String capability, RouteSaveCommand cmd, String operator) {
        if (cmd == null || cmd.primary() == null) throw new BizException("必须指定主模型");
        Capability cap = Capability.of(capability);
        RouteTarget primary = target(cmd.primary());
        List<RouteTarget> fallbacks = cmd.fallbacks() == null ? List.of()
                : cmd.fallbacks().stream().map(GatewayAdminAppService::target).toList();
        Map<Long, ModelProvider> providers = providerRepository.findAll().stream()
                .collect(Collectors.toMap(ModelProvider::id, x -> x));
        for (RouteTarget t : listOf(primary, fallbacks)) {
            ModelProvider p = providers.get(t.providerId());
            if (p == null) throw new BizException("供应商不存在：" + t.providerId());
            if (!p.isMock() && !p.supports(t.model(), cap)) {
                throw new BizException(p.name() + "/" + t.model() + " 不支持 " + cap);
            }
        }
        ModelRoute existing = routeRepository.find(cap).orElse(null);
        int timeout = cmd.timeoutMs() != null ? cmd.timeoutMs()
                : existing != null ? existing.timeoutMs() : GatewayDefaults.DEFAULT_TIMEOUT_MS;
        int retries = cmd.maxRetries() != null ? cmd.maxRetries() : existing != null ? existing.maxRetries() : 1;
        ThinkingMode thinking = cmd.thinking() != null ? ThinkingMode.of(cmd.thinking())
                : existing != null ? existing.thinking() : ThinkingMode.DISABLED;
        LocalDateTime now = LocalDateTime.now(clock);
        ModelRoute route;
        if (existing == null) {
            route = ModelRoute.restore(cap, primary, fallbacks, timeout, retries, thinking, now, operator);
        } else {
            existing.reconfigure(primary, fallbacks, timeout, retries, thinking, operator, now);
            route = existing;
        }
        routeRepository.save(route);
        changed(operator, "ROUTE_UPDATE", "route:" + cap, json(toView(route)));
        return toView(route);
    }

    /**
     * 兼容旧 PUT /ai/provider：把 CHAT、TOOLS 主目标切到名为 provider 的供应商（取其首个支持该能力的模型）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String switchLegacyProvider(String providerName, String operator) {
        ModelProvider p = providerRepository.findAll().stream()
                .filter(x -> x.name().equalsIgnoreCase(providerName == null ? "" : providerName.trim()))
                .findFirst().orElseThrow(() -> new BizException("非法 provider：" + providerName));
        LocalDateTime now = LocalDateTime.now(clock);
        for (Capability cap : List.of(Capability.CHAT, Capability.TOOLS)) {
            ModelSpec spec = p.firstModelFor(cap).orElseThrow(() -> new BizException(p.name() + " 不支持 " + cap));
            ModelRoute route = routeRepository.find(cap).orElseThrow(() -> new BizException("路由未初始化：" + cap));
            route.switchPrimary(new RouteTarget(p.id(), spec.model()), operator, now);
            routeRepository.save(route);
        }
        changed(operator, "ROUTE_SWITCH_LEGACY", "route:CHAT,TOOLS", json(Map.of("provider", p.name())));
        return p.name().toLowerCase();
    }

    // ===================================================================== 用量

    public UsageView usage(String range) {
        String r = "7d".equalsIgnoreCase(range) ? "7d" : "today";
        LocalDateTime from = "7d".equals(r) ? LocalDateTime.now(clock).minusDays(7)
                : LocalDate.now(clock).atStartOfDay();
        Map<String, List<GatewayCallRecord>> groups = new LinkedHashMap<>();
        for (GatewayCallRecord rec : callLogRepository.since(from)) {
            String k = rec.capability() + "|" + rec.provider() + "|" + rec.model();
            groups.computeIfAbsent(k, x -> new ArrayList<>()).add(rec);
        }
        List<UsageRow> rows = new ArrayList<>();
        for (List<GatewayCallRecord> g : groups.values()) {
            GatewayCallRecord first = g.get(0);
            long fails = g.stream().filter(x -> !x.ok()).count();
            List<Long> ms = g.stream().map(GatewayCallRecord::ms).sorted().toList();
            long p95 = ms.get(Math.max(0, (int) Math.ceil(ms.size() * 0.95) - 1));
            BigDecimal cost = g.stream().map(x -> x.costFen() == null ? BigDecimal.ZERO : x.costFen())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new UsageRow(first.capability() == null ? null : first.capability().name(), first.provider(),
                    first.model(), g.size(), (double) fails / g.size(), p95, cost));
        }
        rows.sort(Comparator.comparing(UsageRow::calls).reversed());
        return new UsageView(r, rows);
    }

    // ===================================================================== 内部

    private void changed(String operator, String action, String target, String detail) {
        auditRepository.append(operator, action, target, detail);
        eventPublisher.publish(new GatewayConfigChangedEvent(action + " " + target));
        try {
            changeBus.publishChanged(action + " " + target);
        } catch (RuntimeException e) {
            log.warn("网关变更广播失败（其他实例将在缓存过期后生效）: {}", e.getMessage());
        }
    }

    private String encryptKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return null;
        if (!cipher.available()) {
            throw new BizException(400, "未配置 GUDU_SECRET_KEY，不能在后台保存 key；请改用环境变量来源的 key");
        }
        return cipher.encrypt(apiKey.trim());
    }

    /** 删减模型时，被路由引用的模型不能删。 */
    private void assertModelsStillRouted(Long providerId, List<ModelSpec> specs) {
        Set<String> names = specs.stream().map(ModelSpec::model).collect(Collectors.toSet());
        for (ModelRoute r : routeRepository.findAll()) {
            for (RouteTarget t : r.chain()) {
                if (t.providerId().equals(providerId) && !names.contains(t.model())) {
                    throw new BizException(409, "模型 " + t.model() + " 被路由 " + r.capability() + " 引用，不能删除");
                }
            }
        }
    }

    ProviderView toView(ModelProvider p) {
        String masked = null;
        String source = p.keySource().name();
        if (p.keySource() == KeySource.DB) {
            try {
                masked = cipher.available() ? KeyMasker.mask(cipher.decrypt(p.encryptedKey())) : "****";
            } catch (RuntimeException e) {
                masked = "****";
            }
        } else if (p.keySource() == KeySource.ENV) {
            masked = gateway.hasKey(p) ? KeyMasker.mask(gateway.resolveKey(p)) : null;
            if (masked == null) source = KeySource.NONE.name();
        }
        return new ProviderView(p.id(), p.name(), p.type().name(), p.baseUrl(), masked, source, p.enabled(),
                p.models().stream().map(GatewayAdminAppService::toView).toList(),
                new HealthView(p.health().ok(), p.health().latencyMs(), p.health().checkedAt()));
    }

    static ModelView toView(ModelSpec m) {
        PeakHours ph = m.peakHours();
        return new ModelView(m.model(), m.capabilities().stream().map(Enum::name).sorted().toList(),
                new PriceView(m.price().inputCacheHitFen(), m.price().inputFen(), m.price().outputFen()),
                ph == null ? null : new PeakHoursView(ph.timezone(), ph.weekdaysOnly(), ph.ranges(), ph.offPeakRatio()),
                m.supportsThinking());
    }

    static RouteView toView(ModelRoute r) {
        return new RouteView(r.capability().name(), new TargetView(r.primary().providerId(), r.primary().model()),
                r.fallbacks().stream().map(t -> new TargetView(t.providerId(), t.model())).toList(),
                r.timeoutMs(), r.maxRetries(), r.thinking().name(), r.updatedAt(), r.updatedBy());
    }

    static List<ModelSpec> toSpecs(List<ModelView> views) {
        if (views == null) return List.of();
        List<ModelSpec> out = new ArrayList<>();
        for (ModelView v : views) {
            EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);
            if (v.capabilities() != null) v.capabilities().forEach(c -> caps.add(Capability.of(c)));
            PriceView pv = v.price();
            ModelPrice price = pv == null ? ModelPrice.FREE
                    : new ModelPrice(pv.inputCacheHitFen(), pv.inputFen(), pv.outputFen());
            PeakHoursView ph = v.peakHours();
            PeakHours peak = ph == null ? null
                    : new PeakHours(ph.timezone(), ph.weekdaysOnly(), ph.ranges(), ph.offPeakRatio());
            out.add(new ModelSpec(v.model(), caps, price, peak, v.supportsThinking()));
        }
        return out;
    }

    private static RouteTarget target(TargetView v) {
        if (v == null) throw new BizException("路由目标不能为空");
        return new RouteTarget(v.providerId(), v.model());
    }

    private static List<RouteTarget> listOf(RouteTarget primary, List<RouteTarget> rest) {
        List<RouteTarget> all = new ArrayList<>(rest.size() + 1);
        all.add(primary);
        all.addAll(rest);
        return all;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String describe(ModelProvider p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name());
        m.put("baseUrl", p.baseUrl());
        m.put("enabled", p.enabled());
        m.put("keySource", p.keySource().name());
        m.put("models", p.models().stream().map(ModelSpec::model).toList());
        return json(m);
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }
}
