package com.gudu.xsd.modules.agent.application.assess;

import com.gudu.xsd.modules.ai.gateway.application.GatewayDefaults;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.GatewayCallRecord;
import com.gudu.xsd.modules.ai.gateway.domain.model.KeySource;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelPrice;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelSpec;
import com.gudu.xsd.modules.ai.gateway.domain.model.ProviderType;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.port.EnvKeyLookup;
import com.gudu.xsd.modules.ai.gateway.domain.port.ImageInliner;
import com.gudu.xsd.modules.ai.gateway.domain.port.ModelTransport;
import com.gudu.xsd.modules.ai.gateway.domain.port.SecretCipher;
import com.gudu.xsd.modules.ai.gateway.domain.port.TransportException;
import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayCallLogRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelProviderRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelRouteRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 评测用模型网关：每条用例新建一个独立实例（不读写库表、不写调用日志），并按 fixturePatch.gateway 注入故障：
 * {@code primary: "TIMEOUT"} 主模型超时、{@code fallbacks: "TIMEOUT"} 后备也超时（最终落到规则兜底，done.degraded=true）。
 *
 * <ul>
 *   <li>mock 模式：两个模拟供应商（主 / 后备）的「模型」就是规则意图解析，走完整的网关降级路径；</li>
 *   <li>gateway 模式：用线上网关当前的供应商与路由（或内置默认）+ 真实传输层。</li>
 * </ul>
 */
public final class AssessGateways {

    private AssessGateways() {
    }

    /** 按用例的 gateway 补丁创建网关。 */
    public interface Factory {
        ModelGateway create(Map<String, Object> gatewayPatch);

        String mode();
    }

    // ================================================================== mock 模式

    public static Factory simulated() {
        return new Factory() {
            @Override
            public ModelGateway create(Map<String, Object> patch) {
                ModelSpec sim = new ModelSpec("sim", EnumSet.allOf(Capability.class), ModelPrice.FREE, null, false);
                ModelProvider primary = ModelProvider.restore(1L, "SimPrimary", ProviderType.OPENAI_COMPATIBLE,
                        "http://sim.local", null, KeySource.ENV, "SIM", true, List.of(sim), null);
                ModelProvider fallback = ModelProvider.restore(2L, "SimFallback", ProviderType.OPENAI_COMPATIBLE,
                        "http://sim.local", null, KeySource.ENV, "SIM", true, List.of(sim), null);
                ModelProvider mock = GatewayDefaults.providers().get(2);
                List<ModelRoute> routes = new ArrayList<>();
                for (Capability c : Capability.values()) {
                    routes.add(ModelRoute.restore(c, new RouteTarget(1L, "sim"),
                            List.of(new RouteTarget(2L, "sim"), new RouteTarget(GatewayDefaults.MOCK_ID, "mock")),
                            1_000, 0, ThinkingMode.DISABLED, null, "assess"));
                }
                ModelTransport transport = faulty(patch, "SimPrimary", (ep, key, req, thinking, timeout) -> simulate(req));
                return build(List.of(primary, fallback, mock), routes, transport, name -> "sim", null);
            }

            @Override
            public String mode() {
                return "mock";
            }
        };
    }

    private static ModelReply simulate(ChatRequest req) {
        if (req.ruleFallback() != null) return req.ruleFallback().reply(req);
        return ModelReply.text("{}");
    }

    // ================================================================== gateway 模式

    /**
     * 真实模型：沿用给定的供应商与路由快照（线上网关当前配置），key 由传入的密钥端口解析。
     */
    public static Factory live(Collection<ModelProvider> providers, Collection<ModelRoute> routes, ModelTransport real,
                               SecretCipher cipher, EnvKeyLookup envKeys) {
        return new Factory() {
            @Override
            public ModelGateway create(Map<String, Object> patch) {
                ModelRoute tools = routes.stream().filter(r -> r.capability() == Capability.TOOLS).findFirst()
                        .orElse(GatewayDefaults.routes().get(1));
                String primaryName = providers.stream().filter(p -> p.id().equals(tools.primary().providerId()))
                        .map(ModelProvider::name).findFirst().orElse("DeepSeek");
                return build(List.copyOf(providers), List.copyOf(routes), faulty(patch, primaryName, real), envKeys, cipher);
            }

            @Override
            public String mode() {
                return "gateway";
            }
        };
    }

    /** 内置默认配置 + 环境变量 key（命令行评测）。 */
    public static Factory defaults(ModelTransport real, EnvKeyLookup envKeys) {
        return live(GatewayDefaults.providers(), GatewayDefaults.routes(), real, null, envKeys);
    }

    // ================================================================== 内部

    private static ModelTransport faulty(Map<String, Object> patch, String primaryName, ModelTransport inner) {
        boolean primaryDown = patch != null && "TIMEOUT".equals(String.valueOf(patch.get("primary")));
        boolean fallbacksDown = patch != null && "TIMEOUT".equals(String.valueOf(patch.get("fallbacks")));
        return (ep, key, req, thinking, timeout) -> {
            boolean isPrimary = ep.providerName().equals(primaryName);
            if ((isPrimary && primaryDown) || (!isPrimary && fallbacksDown)) {
                throw TransportException.timeout(ep.providerName() + " 超时（评测注入）");
            }
            return inner.call(ep, key, req, thinking, timeout);
        };
    }

    private static ModelGateway build(List<ModelProvider> providers, List<ModelRoute> routes, ModelTransport transport,
                                      EnvKeyLookup envKeys, SecretCipher cipher) {
        ModelProviderRepository pr = new ModelProviderRepository() {
            @Override
            public List<ModelProvider> findAll() {
                return providers;
            }

            @Override
            public Optional<ModelProvider> findById(Long id) {
                return providers.stream().filter(p -> p.id().equals(id)).findFirst();
            }

            @Override
            public Optional<ModelProvider> findByName(String name) {
                return providers.stream().filter(p -> p.name().equals(name)).findFirst();
            }

            @Override
            public void save(ModelProvider provider) {
                throw new UnsupportedOperationException("评测网关只读");
            }

            @Override
            public void delete(Long id) {
                throw new UnsupportedOperationException("评测网关只读");
            }
        };
        ModelRouteRepository rr = new ModelRouteRepository() {
            @Override
            public List<ModelRoute> findAll() {
                return routes;
            }

            @Override
            public Optional<ModelRoute> find(Capability capability) {
                return routes.stream().filter(r -> r.capability() == capability).findFirst();
            }

            @Override
            public void save(ModelRoute route) {
                throw new UnsupportedOperationException("评测网关只读");
            }
        };
        GatewayCallLogRepository noLog = new GatewayCallLogRepository() {
            @Override
            public void append(GatewayCallRecord record) {
                // 评测不写线上调用日志
            }

            @Override
            public List<GatewayCallRecord> since(LocalDateTime from) {
                return List.of();
            }
        };
        SecretCipher c = cipher != null ? cipher : new SecretCipher() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String encrypt(String plain) {
                throw new IllegalStateException("评测网关不加密");
            }

            @Override
            public String decrypt(String cipherText) {
                throw new IllegalStateException("评测网关不解密");
            }
        };
        ImageInliner noInline = url -> {
            throw new IllegalArgumentException("评测不下载图片");
        };
        return new ModelGateway(pr, rr, noLog, transport, c, envKeys, noInline, Clock.systemDefaultZone());
    }
}
