package com.gudu.xsd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关后台 E2E：总览（key 只有掩码）、供应商增改删（E2E 前缀，MOCK 类型不发外部请求）、引用中删除 409、
 * 路由原值回写（不改变 staging 行为）、用量、权限。不调用真实外部模型。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:e2e-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GuduE2EGatewayTest {

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 总览_只返回掩码_种子路由齐全() {
        String admin = login("admin", "admin123");
        JsonNode g = get(admin, "/admin/ai/gateway");
        assertThat(g.get("code").asInt()).as(g.toString()).isZero();
        assertThat(g.toString()).doesNotContainPattern("sk-[A-Za-z0-9]{12,}");
        JsonNode ds = find(g.get("data").get("providers"), "name", "DeepSeek");
        assertThat(ds).isNotNull();
        assertThat(ds.has("apiKey")).isFalse();
        assertThat(ds.get("models").toString()).contains("deepseek-flash", "deepseek-v4-pro");
        List<String> caps = new java.util.ArrayList<>();
        g.get("data").get("routes").forEach(r -> caps.add(r.get("capability").asText()));
        assertThat(caps).contains("CHAT", "TOOLS", "VISION");
    }

    @Test
    void 供应商增改删_连通性测试_引用中删除409() {
        String admin = login("admin", "admin123");
        Map<String, Object> model = Map.of("model", "e2e-mock", "capabilities", List.of("CHAT"),
                "price", Map.of("inputCacheHitFen", 0, "inputFen", 0, "outputFen", 0), "supportsThinking", false);
        JsonNode created = post(admin, "/admin/ai/gateway/providers",
                Map.of("name", "E2E-Mock", "type", "MOCK", "enabled", true, "models", List.of(model)));
        assertThat(created.get("code").asInt()).as(created.toString()).isZero();
        long id = created.get("data").get("providerId").asLong();

        JsonNode test = post(admin, "/admin/ai/gateway/providers/" + id + "/test", Map.of("capability", "CHAT"));
        assertThat(test.get("data").get("ok").asBoolean()).isTrue();

        JsonNode updated = put(admin, "/admin/ai/gateway/providers/" + id, Map.of("enabled", false));
        assertThat(updated.get("data").get("enabled").asBoolean()).isFalse();

        JsonNode referenced = delete(admin, "/admin/ai/gateway/providers/1");
        assertThat(referenced.get("code").asInt()).as("DeepSeek 被路由引用").isEqualTo(409);

        assertThat(delete(admin, "/admin/ai/gateway/providers/" + id).get("code").asInt()).isZero();
        assertThat(find(get(admin, "/admin/ai/gateway").get("data").get("providers"), "name", "E2E-Mock")).isNull();
    }

    @Test
    void 路由原值回写_记录操作人_用量可查() {
        String admin = login("admin", "admin123");
        JsonNode chat = find(get(admin, "/admin/ai/gateway").get("data").get("routes"), "capability", "CHAT");
        Map<String, Object> body = new HashMap<>();
        body.put("primary", om.convertValue(chat.get("primary"), Map.class));
        body.put("fallbacks", om.convertValue(chat.get("fallbacks"), List.class));
        body.put("timeoutMs", chat.get("timeoutMs").asInt());
        body.put("maxRetries", chat.get("maxRetries").asInt());
        body.put("thinking", chat.get("thinking").asText());
        JsonNode r = put(admin, "/admin/ai/gateway/routes/CHAT", body);
        assertThat(r.get("code").asInt()).as(r.toString()).isZero();
        assertThat(r.get("data").get("updatedBy").asText()).startsWith("member:");
        assertThat(r.get("data").get("primary").toString()).isEqualTo(chat.get("primary").toString());

        JsonNode bad = put(admin, "/admin/ai/gateway/routes/VISION", Map.of(
                "primary", Map.of("providerId", 1, "model", "deepseek-v4-pro"), "fallbacks", List.of()));
        assertThat(bad.get("code").asInt()).as("v4-pro 不支持视觉").isNotZero();

        JsonNode usage = get(admin, "/admin/ai/gateway/usage?range=7d");
        assertThat(usage.get("code").asInt()).isZero();
        assertThat(usage.get("data").get("range").asText()).isEqualTo("7d");
        assertThat(get(admin, "/ai/provider").get("code").asInt()).as("旧接口兼容").isZero();
    }

    @Test
    void 非管理员403() {
        String chef = login("13900000088", "chef123");
        assertThat(get(chef, "/admin/ai/gateway").get("code").asInt()).isEqualTo(403);
    }

    // ================================================================== 工具

    private static JsonNode find(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) if (value.equals(n.get(field).asText())) return n;
        return null;
    }

    private String login(String user, String pwd) {
        JsonNode r = exchange(null, "/auth/login", HttpMethod.POST, Map.of("username", user, "password", pwd));
        assertThat(r.get("code").asInt()).as("登录 " + user).isZero();
        return r.get("data").get("token").asText();
    }

    private JsonNode get(String token, String path) {
        return exchange(token, path, HttpMethod.GET, null);
    }

    private JsonNode post(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.POST, body);
    }

    private JsonNode put(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.PUT, body);
    }

    private JsonNode delete(String token, String path) {
        return exchange(token, path, HttpMethod.DELETE, null);
    }

    private JsonNode exchange(String token, String path, HttpMethod method, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("Authorization", token);
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            String raw = http.exchange(path, method, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), h),
                    String.class).getBody();
            return om.readTree(raw == null ? "{}" : raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
