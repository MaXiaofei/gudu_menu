package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolCall;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;
import com.gudu.xsd.modules.ai.gateway.domain.port.ModelTransport;
import com.gudu.xsd.modules.ai.gateway.domain.service.KeyMasker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 加解密与掩码、OpenAI 兼容请求体与响应解析（不发网络请求）。 */
class GatewayInfraTest {

    private final ObjectMapper om = new ObjectMapper();
    private final OpenAiCompatibleTransport transport = new OpenAiCompatibleTransport(om);

    @Test
    void AES_GCM_往返_每次密文不同_换主密钥解不开() {
        AesGcmSecretCipher c = new AesGcmSecretCipher("master-1");
        String a = c.encrypt("sk-secret-value");
        String b = c.encrypt("sk-secret-value");
        assertThat(a).isNotEqualTo(b).startsWith("v1:");
        assertThat(c.decrypt(a)).isEqualTo("sk-secret-value");
        assertThatThrownBy(() -> new AesGcmSecretCipher("master-2").decrypt(a)).isInstanceOf(IllegalStateException.class);
        assertThat(new AesGcmSecretCipher(" ").available()).isFalse();
    }

    @Test
    void 掩码与日志脱敏() {
        assertThat(KeyMasker.mask("sk-1234567890abcd")).isEqualTo("sk-****abcd");
        assertThat(KeyMasker.mask("short")).isEqualTo("****");
        assertThat(KeyMasker.mask(null)).isNull();
        assertThat(KeyMasker.scrub("Authorization: Bearer abcdefghijk failed for sk-ABCDEFGH12345", null))
                .doesNotContain("abcdefghijk").doesNotContain("ABCDEFGH12345");
    }

    @Test
    void 请求体_工具_JSON模式_思考关闭显式下发_图片content块() throws Exception {
        ChatRequest req = new ChatRequest(Capability.VISION, "v", List.of(ChatMessage.system("s"),
                ChatMessage.userWithImage("这是什么", "https://img/a.jpg"),
                ChatMessage.assistantToolCalls(null, List.of(new ToolCall("c1", "get_pantry", "{}")), "我先查余量"),
                ChatMessage.tool("c1", "{\"ok\":true}")),
                List.of(new ToolDef("get_pantry", "查余量", Map.of("type", "object", "properties", Map.of()))), true, null,
                null, null, false);
        ModelTransport.Endpoint ds = new ModelTransport.Endpoint("DeepSeek", "https://api", "deepseek-flash", true);
        JsonNode body = om.readTree(transport.buildBody(ds, req, ThinkingMode.DISABLED));
        assertThat(body.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(body.has("reasoning_effort")).isFalse();
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("tools").get(0).path("function").path("name").asText()).isEqualTo("get_pantry");
        JsonNode user = body.path("messages").get(1);
        assertThat(user.path("content").get(1).path("image_url").path("detail").asText()).isEqualTo("low");
        assertThat(body.path("messages").get(2).has("reasoning_content")).as("思考关闭时不回传").isFalse();

        JsonNode thinkingOn = om.readTree(transport.buildBody(ds, req, ThinkingMode.HIGH));
        assertThat(thinkingOn.path("thinking").path("type").asText()).isEqualTo("enabled");
        assertThat(thinkingOn.path("reasoning_effort").asText()).isEqualTo("high");
        assertThat(thinkingOn.path("messages").get(2).path("reasoning_content").asText()).isEqualTo("我先查余量");

        JsonNode glm = om.readTree(transport.buildBody(new ModelTransport.Endpoint("GLM", "https://g", "glm-4-plus", false),
                req, ThinkingMode.HIGH));
        assertThat(glm.has("thinking")).as("不支持思考的模型不发该字段").isFalse();
    }

    @Test
    void 响应解析_工具调用_思考内容_缓存命中用量() {
        String body = """
                {"choices":[{"message":{"content":null,"reasoning_content":"想一想",
                  "tool_calls":[{"id":"call_1","type":"function","function":{"name":"search_recipes","arguments":"{\\"query\\":\\"鱼\\"}"}}]}}],
                 "usage":{"prompt_tokens":1200,"prompt_cache_hit_tokens":1000,"completion_tokens":50}}
                """;
        ModelReply r = transport.parse(new ModelTransport.Endpoint("DeepSeek", "x", "m", true), body);
        assertThat(r.toolCalls()).hasSize(1);
        assertThat(r.toolCalls().get(0).argumentsJson()).contains("鱼");
        assertThat(r.reasoningContent()).isEqualTo("想一想");
        assertThat(r.usage().cacheHitTokens()).isEqualTo(1000);
        assertThat(r.usage().cacheMissTokens()).isEqualTo(200);
    }

    @Test
    void 图片下载失败识别() {
        assertThat(OpenAiCompatibleTransport.isImageFetchError(400, "{\"error\":\"Failed to download image from url\"}")).isTrue();
        assertThat(OpenAiCompatibleTransport.isImageFetchError(400, "{\"error\":\"invalid model\"}")).isFalse();
        assertThatThrownBy(() -> HttpImageInliner.assertPublicHost("127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpImageInliner.assertPublicHost("192.168.1.2")).isInstanceOf(IllegalArgumentException.class);
    }
}
