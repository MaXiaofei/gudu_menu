package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关后台生产 E2E（由 GuduE2EGatewayTest 改写）：总览（key 只有掩码）、供应商增改删（E2E_PROD_ 前缀，MOCK 类型不发外部请求）、
 * 引用中删除 409（取总览里 CHAT 路由引用的主目标供应商）、路由原值回写（先快照 ai_route，finally 校验）、用量、权限。不调用真实外部模型。
 *
 * <p>写全站表：ai_route（原值回写，快照校验）、ai_gateway_audit（写入产生审计行，用例末按 E2E 档案 actor 精确删除本次新增行）。
 */
class GuduProdE2EGatewayTest extends ProdE2EBase {

    @Test
    void 总览_只返回掩码_种子路由齐全() {
        String admin = loginAdmin();
        JsonNode g = get(admin, "/admin/ai/gateway");
        assertThat(g.get("code").asInt()).as(g.toString()).isZero();
        assertThat(g.toString()).doesNotContainPattern("sk-[A-Za-z0-9]{12,}");
        JsonNode ds = find(g.get("data").get("providers"), "name", "DeepSeek");
        assertThat(ds).isNotNull();
        assertThat(ds.has("apiKey")).isFalse();
        assertThat(ds.get("models").toString()).contains("deepseek-flash", "deepseek-v4-pro");
        List<String> caps = new ArrayList<>();
        g.get("data").get("routes").forEach(r -> caps.add(r.get("capability").asText()));
        assertThat(caps).contains("CHAT", "TOOLS", "VISION");
    }

    @Test
    void 供应商增改删_连通性测试_引用中删除409() {
        String admin = loginAdmin();
        long auditBefore = maxAuditId();
        try {
            Map<String, Object> model = Map.of("model", "e2e-mock", "capabilities", List.of("CHAT"),
                    "price", Map.of("inputCacheHitFen", 0, "inputFen", 0, "outputFen", 0), "supportsThinking", false);
            JsonNode created = post(admin, "/admin/ai/gateway/providers",
                    Map.of("name", P + "Mock", "type", "MOCK", "enabled", true, "models", List.of(model)));
            assertThat(created.get("code").asInt()).as(created.toString()).isZero();
            long id = created.get("data").get("providerId").asLong();

            JsonNode test = post(admin, "/admin/ai/gateway/providers/" + id + "/test", Map.of("capability", "CHAT"));
            assertThat(test.get("data").get("ok").asBoolean()).isTrue();

            JsonNode updated = put(admin, "/admin/ai/gateway/providers/" + id, Map.of("enabled", false));
            assertThat(updated.get("data").get("enabled").asBoolean()).isFalse();

            // 被路由引用的供应商（总览里 CHAT 路由的主目标，只读取 id）删除应 409
            JsonNode chat = find(get(admin, "/admin/ai/gateway").get("data").get("routes"), "capability", "CHAT");
            assertThat(chat).as("总览应有 CHAT 路由").isNotNull();
            long referencedId = chat.get("primary").get("providerId").asLong();
            assertThat(referencedId).isNotEqualTo(id);
            JsonNode referenced = delete(admin, "/admin/ai/gateway/providers/" + referencedId);
            assertThat(referenced.get("code").asInt()).as("被路由引用的供应商 " + referencedId + " 删除应被拒").isEqualTo(409);

            assertThat(delete(admin, "/admin/ai/gateway/providers/" + id).get("code").asInt()).isZero();
            assertThat(find(get(admin, "/admin/ai/gateway").get("data").get("providers"), "name", P + "Mock")).isNull();
        } finally {
            // 自建供应商若未删除，由基类按前缀清理；这里只清本次产生的审计行
            cleanAudit(chefMemberId, auditBefore);
        }
    }

    @Test
    void 路由原值回写_记录操作人_用量可查() {
        String admin = loginAdmin();
        ProdE2ESnapshot routes = snapshot("ai_route", "capability");
        long auditBefore = maxAuditId();
        try {
            JsonNode overview = get(admin, "/admin/ai/gateway").get("data");
            JsonNode chat = find(overview.get("routes"), "capability", "CHAT");
            assertThat(chat).isNotNull();
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

            // 非法路由：v4-pro 不支持视觉，应被拒（不会改动 VISION 路由；若意外成功由快照还原）
            JsonNode ds = find(overview.get("providers"), "name", "DeepSeek");
            assertThat(ds).as("总览应有 DeepSeek 供应商").isNotNull();
            JsonNode bad = put(admin, "/admin/ai/gateway/routes/VISION", Map.of(
                    "primary", Map.of("providerId", ds.get("providerId").asLong(), "model", "deepseek-v4-pro"),
                    "fallbacks", List.of()));
            assertThat(bad.get("code").asInt()).as("v4-pro 不支持视觉").isNotZero();

            JsonNode usage = get(admin, "/admin/ai/gateway/usage?range=7d");
            assertThat(usage.get("code").asInt()).isZero();
            assertThat(usage.get("data").get("range").asText()).isEqualTo("7d");
            assertThat(get(admin, "/ai/provider").get("code").asInt()).as("旧接口兼容").isZero();
        } finally {
            try {
                routes.restoreOrFail();
            } finally {
                cleanAudit(chefMemberId, auditBefore);
            }
        }
    }

    @Test
    void 非管理员403() {
        registerAndLogin(phoneFor("gwUser"), P + "网关普通用户");
        String user = loginAs(phoneFor("gwUser"), PW);
        assertThat(get(user, "/admin/ai/gateway").get("code").asInt()).isEqualTo(403);
    }

    // ================================================================== 工具

    private static JsonNode find(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) if (value.equals(n.get(field).asText())) return n;
        return null;
    }

    private long maxAuditId() {
        Long m = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ai_gateway_audit", Long.class);
        return m == null ? 0 : m;
    }

    /** 只删本次运行中、由 E2E 超管档案产生的审计行（actor 精确匹配 + id 大于用例开始时的最大值）。 */
    private void cleanAudit(long memberId, long afterId) {
        jdbc.update("DELETE FROM ai_gateway_audit WHERE id > ? AND actor = ?", afterId, "member:" + memberId);
    }
}
