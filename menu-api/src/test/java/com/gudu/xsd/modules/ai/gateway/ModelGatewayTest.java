package com.gudu.xsd.modules.ai.gateway;

import com.gudu.xsd.modules.ai.gateway.application.GatewayDefaults;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.application.ModelUnavailableException;
import com.gudu.xsd.modules.ai.gateway.domain.event.GatewayConfigChangedEvent;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.model.TokenUsage;
import com.gudu.xsd.modules.ai.gateway.domain.port.TransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 路由选择、降级链、超时重试、规则兜底、成本、思考模式、热切换失效。 */
class ModelGatewayTest {

    /** 2026-10-16（周五）10:00 北京时间：DeepSeek 高峰。 */
    static final Clock PEAK = Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    GatewayFakes.Providers providers;
    GatewayFakes.Routes routes;
    GatewayFakes.Calls calls;
    GatewayFakes.Transport transport;
    Map<String, String> env;
    ModelGateway gateway;

    @BeforeEach
    void setUp() {
        List<ModelProvider> ps = new ArrayList<>(GatewayDefaults.providers());
        // GLM 默认停用：测试降级链时启用
        ps.get(1).enable(true);
        providers = new GatewayFakes.Providers(ps);
        routes = new GatewayFakes.Routes(GatewayDefaults.routes());
        calls = new GatewayFakes.Calls();
        transport = new GatewayFakes.Transport();
        env = new java.util.HashMap<>(Map.of("DEEPSEEK_API_KEY", "sk-deepseek-1234567890", "GLM_API_KEY", "glm-key-abcdefgh"));
        gateway = new ModelGateway(providers, routes, calls, transport, GatewayFakes.NO_CIPHER, env::get,
                url -> "data:image/png;base64,AAA", PEAK);
    }

    private static ChatRequest chat() {
        return ChatRequest.text("test", "sys", "hi", false);
    }

    @Test
    void 主模型成功_不降级_按高峰价计成本() {
        transport.behavior.put("DeepSeek", k -> new ModelReply("ok", null, List.of(), new TokenUsage(1_000_000, 500_000, 1_000_000)));
        ChatResult r = gateway.chat(chat());
        assertThat(r.provider()).isEqualTo("DeepSeek");
        assertThat(r.model()).isEqualTo("deepseek-flash");
        assertThat(r.degraded()).isFalse();
        // 50万命中×4 + 50万未命中×200 + 100万输出×800（分/百万）= 2 + 100 + 800
        assertThat(r.costFen()).isEqualByComparingTo("902");
        assertThat(calls.rows).hasSize(1);
        assertThat(calls.rows.get(0).ok()).isTrue();
        assertThat(transport.thinking).containsExactly(ThinkingMode.DISABLED);
    }

    @Test
    void 主模型超时_按maxRetries重试后降级到GLM() {
        transport.behavior.put("DeepSeek", k -> {
            throw TransportException.timeout("超时");
        });
        transport.behavior.put("GLM", k -> ModelReply.text("glm ok"));
        ChatResult r = gateway.chat(chat());
        assertThat(r.provider()).isEqualTo("GLM");
        assertThat(r.degraded()).isTrue();
        assertThat(transport.calls).containsExactly("DeepSeek/deepseek-flash", "DeepSeek/deepseek-flash", "GLM/glm-4-plus");
        assertThat(r.attempts()).extracting(a -> a.ok()).containsExactly(false, false, true);
    }

    @Test
    void 不可重试错误_不重试直接换下一个() {
        transport.behavior.put("DeepSeek", k -> {
            throw new TransportException("HTTP 401 sk-deepseek-1234567890 无效", false, 401, false);
        });
        transport.behavior.put("GLM", k -> ModelReply.text("glm ok"));
        ChatResult r = gateway.chat(chat());
        assertThat(transport.calls).containsExactly("DeepSeek/deepseek-flash", "GLM/glm-4-plus");
        assertThat(r.attempts().get(0).error()).as("错误信息里的 key 被脱敏").doesNotContain("1234567890");
    }

    @Test
    void 全部失败_有规则兜底则落到Mock_没有则抛不可用() {
        transport.behavior.put("DeepSeek", k -> {
            throw TransportException.timeout("x");
        });
        transport.behavior.put("GLM", k -> {
            throw TransportException.timeout("y");
        });
        assertThatThrownBy(() -> gateway.chat(chat())).isInstanceOf(ModelUnavailableException.class);
        ChatRequest withRule = new ChatRequest(Capability.CHAT, "test", List.of(ChatMessage.user("hi")), List.of(), false,
                null, null, req -> ModelReply.text("规则回复"), false);
        ChatResult r = gateway.chat(withRule);
        assertThat(r.provider()).isEqualTo("mock");
        assertThat(r.degraded()).isTrue();
        assertThat(r.content()).isEqualTo("规则回复");
    }

    @Test
    void 缺key或停用的供应商被跳过() {
        env.remove("DEEPSEEK_API_KEY");
        transport.behavior.put("GLM", k -> ModelReply.text("glm"));
        assertThat(gateway.chat(chat()).provider()).isEqualTo("GLM");
        assertThat(transport.calls).containsExactly("GLM/glm-4-plus");
        providers.data.get(GatewayDefaults.GLM_ID).enable(false);
        gateway.invalidate();
        ChatRequest withRule = new ChatRequest(Capability.CHAT, "t", List.of(ChatMessage.user("x")), List.of(), false,
                null, null, req -> ModelReply.text("rule"), false);
        assertThat(gateway.chat(withRule).provider()).isEqualTo("mock");
    }

    @Test
    void 思考模式_请求级覆盖与路由配置() {
        transport.behavior.put("DeepSeek", k -> ModelReply.text("ok"));
        gateway.chat(new ChatRequest(Capability.CHAT, "judge", List.of(ChatMessage.user("x")), List.of(), true, null, null,
                null, false, ThinkingMode.LOW));
        routes.data.get(Capability.CHAT).reconfigure(new RouteTarget(GatewayDefaults.DEEPSEEK_ID, GatewayDefaults.DEEPSEEK_PRO),
                List.of(), 30_000, 0, ThinkingMode.HIGH, "admin", null);
        gateway.onConfigChanged(new GatewayConfigChangedEvent("test"));
        gateway.chat(chat());
        assertThat(transport.thinking).containsExactly(ThinkingMode.LOW, ThinkingMode.HIGH);
        assertThat(transport.calls.get(1)).isEqualTo("DeepSeek/deepseek-v4-pro");
    }

    @Test
    void 热切换_缓存命中不读库_收到变更事件后重新加载() {
        transport.behavior.put("DeepSeek", k -> ModelReply.text("ok"));
        transport.behavior.put("GLM", k -> ModelReply.text("glm"));
        gateway.chat(chat());
        gateway.chat(chat());
        assertThat(providers.reads).as("60 秒内复用缓存").isEqualTo(1);
        // 库里的配置被其他实例改了（仓储每次返回新对象，缓存里的是旧快照）
        routes.data.put(Capability.CHAT, ModelRoute.restore(Capability.CHAT,
                new RouteTarget(GatewayDefaults.GLM_ID, GatewayDefaults.GLM_PLUS), List.of(), 30_000, 0,
                ThinkingMode.DISABLED, null, "admin"));
        assertThat(gateway.chat(chat()).provider()).as("未失效前仍用旧配置").isEqualTo("DeepSeek");
        gateway.onConfigChanged(new GatewayConfigChangedEvent("redis:ROUTE_UPDATE"));
        assertThat(gateway.chat(chat()).provider()).isEqualTo("GLM");
        assertThat(providers.reads).isEqualTo(2);
    }

    @Test
    void 库表读取失败时使用内置默认配置() {
        ModelGateway g = new ModelGateway(new GatewayFakes.Providers(List.of()) {
            @Override
            public List<ModelProvider> findAll() {
                throw new IllegalStateException("table ai_provider doesn't exist");
            }
        }, routes, calls, transport, GatewayFakes.NO_CIPHER, env::get, url -> url, PEAK);
        transport.behavior.put("DeepSeek", k -> ModelReply.text("ok"));
        assertThat(g.chat(chat()).provider()).isEqualTo("DeepSeek");
        assertThat(g.legacyProvider()).isEqualTo("deepseek");
    }

    @Test
    void 视觉_供应商下载图片失败时改用base64重发且不占重试() {
        List<String> images = new ArrayList<>();
        transport.behavior.put("DeepSeek", k -> {
            if (images.isEmpty()) {
                images.add("first");
                throw new TransportException("HTTP 400 failed to download image url", false, 400, true);
            }
            return ModelReply.text("{\"name\":\"番茄\"}");
        });
        ChatResult r = gateway.vision("vision", "https://example.com/a.jpg", "识别", "{}", null);
        assertThat(r.provider()).isEqualTo("DeepSeek");
        assertThat(r.content()).contains("番茄");
        assertThat(transport.calls).containsExactly("DeepSeek/deepseek-flash", "DeepSeek/deepseek-flash");
    }

    @Test
    void 非高峰半价() {
        Clock night = Clock.fixed(Instant.parse("2026-10-16T14:00:00Z"), ZoneId.of("Asia/Shanghai")); // 22:00
        ModelGateway g = new ModelGateway(providers, routes, calls, transport, GatewayFakes.NO_CIPHER, env::get, u -> u, night);
        transport.behavior.put("DeepSeek", k -> new ModelReply("ok", null, List.of(), new TokenUsage(0, 0, 1_000_000)));
        assertThat(g.chat(chat()).costFen()).isEqualByComparingTo(new BigDecimal("400"));
    }
}
