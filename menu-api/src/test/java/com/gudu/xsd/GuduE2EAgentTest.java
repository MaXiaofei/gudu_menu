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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 咕嘟 Agent E2E（真 HTTP + staging 库 / Redis）。模型网关在测试 JVM 里拿不到 DEEPSEEK_API_KEY → 主模型跳过 →
 * GLM 默认停用 → 落到规则意图解析，结果确定。自建数据：会话标题 / 食集名 / 评测版本号以 E2E 开头，e2e-seed.sql [agent] 段清理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:e2e-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GuduE2EAgentTest {

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper om = new ObjectMapper();

    private static final long DISH_FANQIE_CHAODAN = 1L;

    // ================================================================== 场景

    @Test
    void 会话_流式对话事件顺序_消息落库_调用链可查() throws Exception {
        String admin = login("admin", "admin123");
        String sid = createSession(admin, "E2E 冰箱能做啥");
        List<Event> ev = stream(admin, sid, "冰箱里快坏的东西今晚能做啥？");
        assertThat(ev.get(0).name).isEqualTo("meta");
        assertThat(ev.get(ev.size() - 1).name).isEqualTo("done");
        assertThat(ev).extracting(e -> e.name).contains("tool_start", "tool_end", "token");
        assertThat(ev.get(ev.size() - 1).data.get("usage").has("costFen")).isTrue();
        String traceId = ev.get(0).data.get("traceId").asText();
        String messageId = ev.get(0).data.get("messageId").asText();

        JsonNode msgs = get(admin, "/agent/sessions/" + sid + "/messages?size=10");
        assertThat(msgs.get("code").asInt()).isZero();
        JsonNode items = msgs.get("data").get("items");
        assertThat(items).hasSize(2);
        JsonNode assistant = items.get(0);
        assertThat(assistant.get("messageId").asText()).isEqualTo(messageId);
        assertThat(assistant.get("tools").size()).isPositive();
        assertThat(assistant.get("tools").get(0).get("name").asText()).isEqualTo("get_pantry");

        JsonNode sessions = get(admin, "/agent/sessions?size=5");
        assertThat(sessions.get("data").get("items").get(0).get("sessionId").asText()).isEqualTo(sid);

        JsonNode trace = get(admin, "/admin/agent/traces/" + traceId);
        assertThat(trace.get("code").asInt()).as(trace.toString()).isZero();
        assertThat(trace.get("data").get("trace").get("promptVersion").asText()).isEqualTo("agent-v1");
        List<String> spanNames = new ArrayList<>();
        trace.get("data").get("spans").forEach(s -> spanNames.add(s.get("name").asText()));
        assertThat(spanNames).contains("guard.input", "llm.plan", "tool.get_pantry", "guard.output");
        assertThat(get(admin, "/admin/agent/traces/" + traceId + "/messages").get("data")).hasSize(2);
        assertThat(get(admin, "/admin/agent/traces?size=5").get("data").get("items").size()).isPositive();
        JsonNode stats = get(admin, "/admin/agent/stats");
        assertThat(stats.get("data").get("turns").asLong()).isPositive();
    }

    @Test
    void 写操作_确认前不落库_他人无权确认_确认生效_重复确认拒绝_撤销恢复() throws Exception {
        String admin = login("admin", "admin123");
        String chef = login("13900000088", "chef123");
        long menuId = createMenu(admin, "E2E咕嘟食集");
        String sid = createSession(admin, "E2E 写操作");

        List<Event> ev = stream(admin, sid, "把番茄炒蛋加进E2E咕嘟食集里");
        Event action = ev.stream().filter(e -> e.name.equals("action")).findFirst().orElseThrow(
                () -> new AssertionError("应生成待确认动作：" + names(ev)));
        String actionId = action.data.get("actionId").asText();
        assertThat(action.data.get("tool").asText()).isEqualTo("add_to_menu");
        assertThat(action.data.get("status").asText()).isEqualTo("PENDING");
        assertThat(action.data.get("args").get("dishes").get(0).asText()).isEqualTo("番茄炒蛋");
        assertThat(menuDishes(admin, menuId)).as("确认前不落库").isEmpty();

        JsonNode other = post(chef, "/agent/actions/" + actionId + "/confirm", null);
        assertThat(other.get("code").asInt()).as("他人不能确认").isNotZero();

        JsonNode ok = post(admin, "/agent/actions/" + actionId + "/confirm", null);
        assertThat(ok.get("code").asInt()).as(ok.toString()).isZero();
        String undo = ok.get("data").get("undoToken").asText();
        assertThat(menuDishes(admin, menuId)).contains(DISH_FANQIE_CHAODAN);
        assertThat(post(admin, "/agent/actions/" + actionId + "/confirm", null).get("code").asInt()).as("重复确认").isNotZero();

        JsonNode undone = post(admin, "/agent/actions/undo", Map.of("undoToken", undo));
        assertThat(undone.get("code").asInt()).as(undone.toString()).isZero();
        assertThat(menuDishes(admin, menuId)).as("撤销后恢复").isEmpty();
        JsonNode msgs = get(admin, "/agent/sessions/" + sid + "/messages?size=10");
        assertThat(msgs.get("data").get("items").get(0).get("actions").get(0).get("status").asText()).isEqualTo("UNDONE");
    }

    @Test
    void 修改待确认卡_旧卡被替代_新卡重算预览() throws Exception {
        String admin = login("admin", "admin123");
        String sid = createSession(admin, "E2E 改人份");
        List<Event> ev = stream(admin, sid, "番茄炒蛋做完了，我们两个人吃的");
        Event action = ev.stream().filter(e -> e.name.equals("action")).findFirst().orElseThrow(
                () -> new AssertionError("应生成做完回写卡：" + names(ev)));
        String oldId = action.data.get("actionId").asText();
        assertThat(action.data.get("preview").get("servings").asInt()).isEqualTo(2);

        JsonNode revised = post(admin, "/agent/actions/" + oldId + "/revise", Map.of("args", Map.of("servings", 3)));
        assertThat(revised.get("code").asInt()).as(revised.toString()).isZero();
        assertThat(revised.get("data").get("preview").get("servings").asInt()).isEqualTo(3);
        assertThat(revised.get("data").get("actionId").asText()).isNotEqualTo(oldId);
        assertThat(post(admin, "/agent/actions/" + oldId + "/confirm", null).get("code").asInt())
                .as("旧卡已被替代").isNotZero();
        JsonNode cancel = post(admin, "/agent/actions/" + revised.get("data").get("actionId").asText() + "/cancel", null);
        assertThat(cancel.get("code").asInt()).isZero();
    }

    @Test
    void 越权_他人会话不可读_非管理员不能看调用链() throws Exception {
        String admin = login("admin", "admin123");
        String chef = login("13900000088", "chef123");
        String sid = createSession(admin, "E2E 私有会话");
        assertThat(get(chef, "/agent/sessions/" + sid + "/messages").get("code").asInt()).isEqualTo(403);
        assertThat(get(chef, "/admin/agent/traces").get("code").asInt()).isEqualTo(403);
        assertThat(get(chef, "/admin/agent/eval/runs").get("code").asInt()).isEqualTo(403);
    }

    @Test
    void 差评_调用链标记_回流为评测草稿_评测运行与用例统计() throws Exception {
        String admin = login("admin", "admin123");
        String sid = createSession(admin, "E2E 差评");
        List<Event> ev = stream(admin, sid, "今晚吃啥");
        String messageId = ev.get(0).data.get("messageId").asText();
        String traceId = ev.get(0).data.get("traceId").asText();
        assertThat(post(admin, "/agent/messages/" + messageId + "/feedback", Map.of("rating", -1, "comment", "E2E 不好"))
                .get("code").asInt()).isZero();
        JsonNode t = get(admin, "/admin/agent/traces/" + traceId);
        assertThat(t.get("data").get("trace").get("feedback").asInt()).isEqualTo(-1);
        assertThat(t.get("data").get("trace").get("status").asText()).isEqualTo("DOWNVOTED");

        JsonNode draft = post(admin, "/admin/agent/eval/cases", Map.of("fromTraceId", traceId));
        assertThat(draft.get("code").asInt()).as(draft.toString()).isZero();
        assertThat(draft.get("data").get("status").asText()).isEqualTo("DRAFT");
        JsonNode cases = get(admin, "/admin/agent/eval/cases");
        assertThat(cases.get("data").get("byIntent")).hasSizeGreaterThanOrEqualTo(14);

        JsonNode run = post(admin, "/admin/agent/eval/runs", Map.of("promptVersion", "E2E-mock", "provider", "mock",
                "split", "test", "runs", 1));
        long runId = run.get("data").get("runId").asLong();
        String status = "RUNNING";
        for (int i = 0; i < 60 && "RUNNING".equals(status); i++) {
            Thread.sleep(500);
            for (JsonNode r : get(admin, "/admin/agent/eval/runs").get("data")) {
                if (r.get("runId").asLong() == runId) status = r.get("status").asText();
            }
        }
        assertThat(status).isEqualTo("DONE");
        assertThat(get(admin, "/admin/agent/eval/runs/" + runId + "/failures").get("code").asInt()).isZero();
    }

    @Test
    void 首屏建议() {
        String admin = login("admin", "admin123");
        JsonNode s = get(admin, "/agent/suggestions");
        assertThat(s.get("code").asInt()).as(s.toString()).isZero();
        assertThat(s.get("data").get("greeting").asText()).isNotBlank();
        assertThat(s.get("data").get("prompts").size()).isPositive();
    }

    // ================================================================== 工具

    private record Event(String name, JsonNode data) {
    }

    private static List<String> names(List<Event> ev) {
        return ev.stream().map(e -> e.name + (e.name.equals("tool_end") ? "(" + e.data.get("summary").asText() + ")" : ""))
                .toList();
    }

    private List<Event> stream(String token, String sessionId, String text) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        String body = http.exchange("/agent/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(om.writeValueAsString(Map.of("text", text)), h), String.class).getBody();
        assertThat(body).as("SSE 响应为空").isNotBlank();
        List<Event> out = new ArrayList<>();
        for (String block : body.split("\\r?\\n\\r?\\n")) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\\r?\\n")) {
                if (line.startsWith("event:")) name = line.substring(6).trim();
                else if (line.startsWith("data:")) data.append(line.substring(5));
            }
            if (name != null) out.add(new Event(name, om.readTree(data.toString())));
        }
        return out;
    }

    private String createSession(String token, String title) {
        JsonNode r = post(token, "/agent/sessions", Map.of("title", title));
        assertThat(r.get("code").asInt()).as(r.toString()).isZero();
        return r.get("data").get("sessionId").asText();
    }

    private long createMenu(String token, String name) {
        JsonNode r = post(token, "/menu", Map.of("menu", Map.of("name", name, "servingCount", 2, "status", "ACTIVE"),
                "dishes", List.of()));
        assertThat(r.get("code").asInt()).as(r.toString()).isZero();
        for (JsonNode m : get(token, "/menu?pageNum=1&pageSize=50").get("data").get("records")) {
            if (name.equals(m.get("name").asText())) return m.get("id").asLong();
        }
        throw new AssertionError("新建食集未找到");
    }

    private List<Long> menuDishes(String token, long menuId) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode d : get(token, "/menu/" + menuId).get("data").get("dishes")) ids.add(d.get("dishId").asLong());
        return ids;
    }

    private String login(String user, String pwd) {
        Map<String, String> body = new HashMap<>();
        body.put("username", user);
        body.put("password", pwd);
        JsonNode r = post(null, "/auth/login", body);
        assertThat(r.get("code").asInt()).as("登录 " + user + "：" + r).isZero();
        return r.get("data").get("token").asText();
    }

    private JsonNode get(String token, String path) {
        return exchange(token, path, HttpMethod.GET, null);
    }

    private JsonNode post(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.POST, body);
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
