package com.gudu.xsd.modules.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEvent;
import com.gudu.xsd.modules.ai.gateway.application.GatewayAdminAppService;
import com.gudu.xsd.modules.ai.gateway.application.GatewayDefaults;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ModelView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.PriceView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderSaveCommand;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.RouteSaveCommand;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.TargetView;
import com.gudu.xsd.modules.ai.gateway.domain.event.GatewayConfigChangedEvent;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.port.TransportException;
import com.gudu.xsd.modules.ai.gateway.infrastructure.AesGcmSecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 后台网关：供应商增删改（key 加密 + 掩码）、引用中删除 409、路由校验、连通性测试、兼容旧切换、审计与广播。 */
class GatewayAdminAppServiceTest {

    GatewayFakes.Providers providers;
    GatewayFakes.Routes routes;
    GatewayFakes.Audit audit;
    GatewayFakes.Transport transport;
    List<String> broadcasts;
    List<DomainEvent> events;
    AesGcmSecretCipher cipher;
    GatewayAdminAppService svc;

    @BeforeEach
    void setUp() {
        providers = new GatewayFakes.Providers(GatewayDefaults.providers());
        routes = new GatewayFakes.Routes(GatewayDefaults.routes());
        audit = new GatewayFakes.Audit();
        transport = new GatewayFakes.Transport();
        broadcasts = new ArrayList<>();
        events = new ArrayList<>();
        cipher = new AesGcmSecretCipher("unit-test-master-secret");
        Map<String, String> env = Map.of("DEEPSEEK_API_KEY", "sk-deepseekabcdef9876");
        ModelGateway gw = new ModelGateway(providers, routes, new GatewayFakes.Calls(), transport, cipher, env::get,
                u -> u, Clock.systemDefaultZone());
        svc = new GatewayAdminAppService(providers, routes, audit, new GatewayFakes.Calls(), gw, transport, cipher,
                broadcasts::add, events::add, new ObjectMapper().findAndRegisterModules(), Clock.systemDefaultZone());
    }

    private static ProviderSaveCommand qwen(String key) {
        return new ProviderSaveCommand("Qwen", null, "https://dashscope.example.com/v1", key, true,
                List.of(new ModelView("qwen-vl", List.of("CHAT", "VISION"),
                        new PriceView(BigDecimal.ONE, new BigDecimal("300"), new BigDecimal("900")), null, false)));
    }

    @Test
    void 新增供应商_key加密入库_接口只返回掩码_写审计并广播() {
        ProviderView v = svc.createProvider(qwen("sk-qwen-secret-12345678"), "member:1");
        assertThat(v.apiKeyMasked()).isEqualTo("sk-****5678");
        assertThat(v.keySource()).isEqualTo("DB");
        String stored = providers.data.get(v.providerId()).encryptedKey();
        assertThat(stored).startsWith("v1:").doesNotContain("qwen-secret");
        assertThat(cipher.decrypt(stored)).isEqualTo("sk-qwen-secret-12345678");
        assertThat(audit.rows).hasSize(1).allMatch(r -> !r.contains("qwen-secret"));
        assertThat(broadcasts).hasSize(1);
        assertThat(events).hasSize(1).first().isInstanceOf(GatewayConfigChangedEvent.class);
    }

    @Test
    void 环境变量来源的key_也只返回掩码() {
        ProviderView ds = svc.overview().providers().stream().filter(p -> p.name().equals("DeepSeek")).findFirst().orElseThrow();
        assertThat(ds.keySource()).isEqualTo("ENV");
        assertThat(ds.apiKeyMasked()).isEqualTo("sk-****9876");
        ProviderView glm = svc.overview().providers().stream().filter(p -> p.name().equals("GLM")).findFirst().orElseThrow();
        assertThat(glm.keySource()).as("环境变量没配").isEqualTo("NONE");
        assertThat(glm.apiKeyMasked()).isNull();
    }

    @Test
    void 未配置主密钥时不能保存key() {
        GatewayAdminAppService noKey = new GatewayAdminAppService(providers, routes, audit, new GatewayFakes.Calls(),
                null, transport, new AesGcmSecretCipher(""), broadcasts::add, events::add, new ObjectMapper(),
                Clock.systemDefaultZone());
        assertThatThrownBy(() -> noKey.createProvider(qwen("sk-abc12345678"), "x")).isInstanceOf(BizException.class)
                .hasMessageContaining("GUDU_SECRET_KEY");
    }

    @Test
    void 被路由引用的供应商删除返回409_未引用可删() {
        assertThatThrownBy(() -> svc.deleteProvider(GatewayDefaults.GLM_ID, "x"))
                .isInstanceOf(BizException.class).satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(409));
        ProviderView v = svc.createProvider(qwen(null), "x");
        svc.deleteProvider(v.providerId(), "x");
        assertThat(providers.data).doesNotContainKey(v.providerId());
    }

    @Test
    void 路由_模型必须支持该能力_成功后广播() {
        assertThatThrownBy(() -> svc.updateRoute("VISION", new RouteSaveCommand(
                new TargetView(GatewayDefaults.DEEPSEEK_ID, GatewayDefaults.DEEPSEEK_PRO), List.of(), 60_000, 1, null), "x"))
                .isInstanceOf(BizException.class).hasMessageContaining("不支持 VISION");
        var r = svc.updateRoute("CHAT", new RouteSaveCommand(new TargetView(GatewayDefaults.DEEPSEEK_ID,
                GatewayDefaults.DEEPSEEK_PRO), List.of(new TargetView(GatewayDefaults.MOCK_ID, "mock")), 20_000, 2, "high"),
                "member:1");
        assertThat(r.primary().model()).isEqualTo("deepseek-v4-pro");
        assertThat(r.thinking()).isEqualTo("HIGH");
        assertThat(r.updatedBy()).isEqualTo("member:1");
        assertThat(routes.data.get(Capability.CHAT).timeoutMs()).isEqualTo(20_000);
        assertThat(broadcasts).isNotEmpty();
    }

    @Test
    void 删除被路由引用的模型返回409() {
        ProviderSaveCommand onlyPro = new ProviderSaveCommand(null, null, null, null, null,
                List.of(new ModelView("deepseek-v4-pro", List.of("CHAT", "TOOLS"), null, null, true)));
        assertThatThrownBy(() -> svc.updateProvider(GatewayDefaults.DEEPSEEK_ID, onlyPro, "x"))
                .isInstanceOf(BizException.class).satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(409));
    }

    @Test
    void 连通性测试_成功与失败都记录健康状态_错误脱敏() {
        transport.behavior.put("DeepSeek", k -> ModelReply.text("ok"));
        var ok = svc.testProvider(GatewayDefaults.DEEPSEEK_ID, null, "CHAT", "x");
        assertThat(ok.ok()).isTrue();
        assertThat(ok.sample()).isEqualTo("ok");
        assertThat(providers.data.get(GatewayDefaults.DEEPSEEK_ID).health().ok()).isTrue();
        transport.behavior.put("DeepSeek", k -> {
            throw new TransportException("HTTP 401 key sk-deepseekabcdef9876 invalid", false, 401, false);
        });
        var bad = svc.testProvider(GatewayDefaults.DEEPSEEK_ID, "deepseek-flash", "VISION", "x");
        assertThat(bad.ok()).isFalse();
        assertThat(bad.error()).doesNotContain("abcdef9876");
        assertThat(providers.data.get(GatewayDefaults.DEEPSEEK_ID).health().ok()).isFalse();
    }

    @Test
    void 兼容旧接口_切换provider改写CHAT与TOOLS主目标() {
        String p = svc.switchLegacyProvider("glm", "legacy");
        assertThat(p).isEqualTo("glm");
        assertThat(routes.data.get(Capability.CHAT).primary().providerId()).isEqualTo(GatewayDefaults.GLM_ID);
        assertThat(routes.data.get(Capability.TOOLS).primary().model()).isEqualTo(GatewayDefaults.GLM_PLUS);
        assertThat(routes.data.get(Capability.CHAT).fallbacks()).noneMatch(t -> t.providerId().equals(GatewayDefaults.GLM_ID));
        assertThatThrownBy(() -> svc.switchLegacyProvider("nope", "x")).isInstanceOf(BizException.class);
    }
}
