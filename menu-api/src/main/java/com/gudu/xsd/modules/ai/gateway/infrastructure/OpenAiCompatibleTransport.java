package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.model.TokenUsage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolCall;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;
import com.gudu.xsd.modules.ai.gateway.domain.port.ModelTransport;
import com.gudu.xsd.modules.ai.gateway.domain.port.TransportException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OpenAI 兼容 {@code POST {baseUrl}/chat/completions}（非流式）。
 *
 * <ul>
 *   <li>工具：{@code tools[{type:function,function:{name,description,parameters}}]}，回复里解析 {@code tool_calls}；</li>
 *   <li>视觉：user 消息改为 content 块 {@code [{type:text},{type:image_url,image_url:{url,detail:low}}]}；</li>
 *   <li>思考：支持的模型显式发送 {@code thinking:{type:disabled|enabled}} 与 {@code reasoning_effort}
 *       （DeepSeek 默认开启思考，关闭必须显式声明）；assistant 历史消息带回 {@code reasoning_content}；</li>
 *   <li>用量：{@code prompt_cache_hit_tokens} 单独返回用于缓存命中计价。</li>
 * </ul>
 * key 只出现在 Authorization 头，异常信息不含 key。
 */
@Component
public class OpenAiCompatibleTransport implements ModelTransport {

    private final ObjectMapper objectMapper;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiCompatibleTransport(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    OpenAiCompatibleTransport(ObjectMapper objectMapper, HttpClient http) {
        this.objectMapper = objectMapper;
        this.http = http;
    }

    @Override
    public ModelReply call(Endpoint ep, String apiKey, ChatRequest request, ThinkingMode thinking, int timeoutMs) {
        if (apiKey == null || apiKey.isBlank()) throw new TransportException("未配置 key", false, 401, false);
        String body = buildBody(ep, request, thinking);
        HttpRequest req = HttpRequest.newBuilder(URI.create(ep.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw TransportException.timeout(ep.providerName() + " 超时（" + timeoutMs + "ms）");
        } catch (IOException e) {
            throw new TransportException(ep.providerName() + " 网络错误：" + e.getClass().getSimpleName(), true, 0,
                    false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException(ep.providerName() + " 请求被中断", false, 0, false);
        }
        int status = resp.statusCode();
        if (status / 100 != 2) {
            String text = resp.body() == null ? "" : resp.body();
            boolean retryable = status == 429 || status >= 500;
            throw new TransportException(ep.providerName() + " HTTP " + status + "：" + brief(text), retryable, status,
                    isImageFetchError(status, text));
        }
        return parse(ep, resp.body());
    }

    String buildBody(Endpoint ep, ChatRequest request, ThinkingMode thinking) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", ep.model());
        root.put("stream", false);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage m : request.messages()) {
            messages.add(toJson(m, thinking));
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDef t : request.tools()) {
                ObjectNode fn = objectMapper.createObjectNode();
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.set("parameters", objectMapper.valueToTree(t.parameters()));
                ObjectNode wrap = tools.addObject();
                wrap.put("type", "function");
                wrap.set("function", fn);
            }
        }
        if (request.jsonMode()) {
            root.putObject("response_format").put("type", "json_object");
        }
        if (ep.supportsThinking()) {
            ThinkingMode mode = thinking == null ? ThinkingMode.DISABLED : thinking;
            root.putObject("thinking").put("type", mode.enabled() ? "enabled" : "disabled");
            if (mode.enabled()) root.put("reasoning_effort", mode.effort());
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new TransportException("请求序列化失败", false, 0, false);
        }
    }

    private ObjectNode toJson(ChatMessage m, ThinkingMode thinking) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("role", m.role());
        if (m.imageUrl() != null && "user".equals(m.role())) {
            ArrayNode parts = n.putArray("content");
            if (m.content() != null && !m.content().isBlank()) {
                parts.addObject().put("type", "text").put("text", m.content());
            }
            ObjectNode img = parts.addObject();
            img.put("type", "image_url");
            img.putObject("image_url").put("url", m.imageUrl()).put("detail", "low");
        } else {
            if (m.content() == null) n.putNull("content");
            else n.put("content", m.content());
        }
        if (!m.toolCalls().isEmpty()) {
            ArrayNode calls = n.putArray("tool_calls");
            for (ToolCall c : m.toolCalls()) {
                ObjectNode call = calls.addObject();
                call.put("id", c.id());
                call.put("type", "function");
                call.putObject("function").put("name", c.name()).put("arguments", c.argumentsJson());
            }
        }
        if (m.toolCallId() != null) n.put("tool_call_id", m.toolCallId());
        if (m.reasoningContent() != null && "assistant".equals(m.role()) && thinking != null && thinking.enabled()) {
            n.put("reasoning_content", m.reasoningContent());
        }
        return n;
    }

    ModelReply parse(Endpoint ep, String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new TransportException(ep.providerName() + " 响应不是 JSON", true, 200, false);
        }
        JsonNode msg = root.path("choices").path(0).path("message");
        if (msg.isMissingNode()) throw new TransportException(ep.providerName() + " 响应缺少 choices", true, 200, false);
        String content = msg.path("content").isNull() ? null : msg.path("content").asText(null);
        String reasoning = msg.path("reasoning_content").isMissingNode() || msg.path("reasoning_content").isNull()
                ? null : msg.path("reasoning_content").asText();
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode c : msg.path("tool_calls")) {
            JsonNode fn = c.path("function");
            calls.add(new ToolCall(c.path("id").asText(null), fn.path("name").asText(),
                    fn.path("arguments").isTextual() ? fn.path("arguments").asText() : fn.path("arguments").toString()));
        }
        if ((content == null || content.isBlank()) && calls.isEmpty()) {
            throw new TransportException(ep.providerName() + " 返回内容为空", true, 200, false);
        }
        JsonNode u = root.path("usage");
        TokenUsage usage = new TokenUsage(u.path("prompt_tokens").asInt(0), u.path("prompt_cache_hit_tokens").asInt(0),
                u.path("completion_tokens").asInt(0));
        return new ModelReply(content, reasoning, calls, usage);
    }

    static boolean isImageFetchError(int status, String body) {
        if (status != 400 || body == null) return false;
        String b = body.toLowerCase(Locale.ROOT);
        return b.contains("image") && (b.contains("download") || b.contains("fetch") || b.contains("url")
                || b.contains("timeout") || b.contains("access"));
    }

    private static String brief(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }
}
