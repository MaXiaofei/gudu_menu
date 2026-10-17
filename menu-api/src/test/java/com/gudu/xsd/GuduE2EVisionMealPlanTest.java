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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E：拍照识别 + 一周餐程 + 营养报告（showcase-ai-community §4 §5，V72）。
 * 识别走 MockVisionProvider（模型网关未接入视觉能力时的默认路由），结果确定。
 * 自建数据：识别入库只用「E2E视觉」前缀新食材；餐程生成的食集归属 admin（非演示成员），
 * 由 e2e-seed.sql 的 [vision/mealplan] 区块与通用清理兜底。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:e2e-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GuduE2EVisionMealPlanTest {

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper om = new ObjectMapper();

    private static final String IMG = "/gudu/uploads/original/20261016/e2e-vision.jpg";

    // ===================== 拍照识别 =====================

    @Test
    void A_冰箱识别_分级与别名对齐_放进余量只能一次() throws Exception {
        String token = loginAdmin();
        JsonNode created = post(token, "/vision/jobs", Map.of("mode", "FRIDGE", "imageUrl", IMG));
        assertThat(created.get("code").asInt()).as("建任务 msg=" + text(created, "msg")).isEqualTo(0);
        long jobId = created.get("data").get("jobId").asLong();

        JsonNode job = waitDone(token, jobId);
        JsonNode items = job.get("items");
        assertThat(items.size()).as("柠檬 0.31 被丢弃，剩 9 项").isEqualTo(9);
        int confirm = 0;
        boolean tomatoAligned = false;
        for (JsonNode it : items) {
            if ("CONFIRM".equals(it.get("level").asText())) confirm++;
            if ("西红柿".equals(it.get("rawName").asText()) && "番茄".equals(it.get("name").asText())) {
                tomatoAligned = !it.get("ingredientId").isNull();
            }
        }
        assertThat(confirm).isEqualTo(2);
        assertThat(tomatoAligned).as("西红柿 → 番茄 并带食材 id").isTrue();
        assertThat(text(job, "imageExpiresAt")).isNotBlank();

        Map<String, Object> item = new HashMap<>();
        item.put("name", "E2E视觉菜心");
        item.put("amount", 1);
        item.put("unit", "把");
        item.put("bestBefore", LocalDate.now().plusDays(5).toString());
        JsonNode applied = post(token, "/vision/jobs/" + jobId + "/apply", Map.of("items", List.of(item)));
        assertThat(applied.get("code").asInt()).as("入库 msg=" + text(applied, "msg")).isEqualTo(0);
        assertThat(applied.get("data").get("added").asInt()).isEqualTo(1);

        JsonNode again = post(token, "/vision/jobs/" + jobId + "/apply", Map.of("items", List.of(item)));
        assertThat(again.get("code").asInt()).as("重复入库应被拒").isNotEqualTo(0);
    }

    @Test
    void B_菜谱截图识别_外链图片被拒() throws Exception {
        String token = loginAdmin();
        JsonNode created = post(token, "/vision/jobs", Map.of("mode", "RECIPE", "imageUrl", IMG));
        assertThat(created.get("code").asInt()).isEqualTo(0);
        JsonNode job = waitDone(token, created.get("data").get("jobId").asLong());
        assertThat(job.get("recipe").get("title").asText()).isEqualTo("滑蛋虾仁");
        assertThat(job.get("recipe").get("steps").size()).isEqualTo(4);
        assertThat(job.get("recipe").get("ingredients").size()).isGreaterThanOrEqualTo(3);

        JsonNode evil = post(token, "/vision/jobs", Map.of("mode", "FRIDGE", "imageUrl", "https://evil.example/x.jpg"));
        assertThat(evil.get("code").asInt()).as("外链图片应被拒").isNotEqualTo(0);
        JsonNode badMode = post(token, "/vision/jobs", Map.of("mode", "CAMERA", "imageUrl", IMG));
        assertThat(badMode.get("code").asInt()).isNotEqualTo(0);
    }

    // ===================== 一周餐程 =====================

    @Test
    void C_一周餐程_周一开始_换一道_生成食集与采购() {
        String token = loginAdmin();
        JsonNode created = post(token, "/mealplan/drafts", Map.of("meals", List.of("LUNCH", "DINNER"),
                "weekdayMaxMinutes", 60, "preferExpiring", true));
        assertThat(created.get("code").asInt()).as("生成 msg=" + text(created, "msg")).isEqualTo(0);
        JsonNode draft = created.get("data");
        long draftId = draft.get("draftId").asLong();
        LocalDate weekStart = LocalDate.parse(draft.get("weekStart").asText());
        assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(LocalDate.parse(draft.get("weekEnd").asText())).isEqualTo(weekStart.plusDays(6));
        assertThat(weekStart).isAfterOrEqualTo(LocalDate.now());
        assertThat(draft.get("days").size()).isEqualTo(7);
        assertThat(draft.get("days").get(0).get("weekday").asText()).isEqualTo("周一");
        assertThat(draft.get("check").get("notes").size()).isGreaterThan(0);
        assertThat(draft.get("constraints").toString()).contains("口人");

        Set<Long> inWeek = new HashSet<>();
        long firstSlot = -1;
        long secondDish = -1;
        for (JsonNode day : draft.get("days")) {
            for (JsonNode slot : day.get("slots")) {
                assertThat(inWeek.add(slot.get("dish").get("id").asLong())).as("本周不重样").isTrue();
                assertThat(slot.get("dish").get("coverage")).isNotNull();
                if (firstSlot < 0) firstSlot = slot.get("slotId").asLong();
                else if (secondDish < 0) secondDish = slot.get("dish").get("id").asLong();
            }
        }
        assertThat(inWeek).isNotEmpty();

        JsonNode alts = post(token, "/mealplan/drafts/" + draftId + "/slots/" + firstSlot + "/alternatives", null);
        assertThat(alts.get("code").asInt()).isEqualTo(0);
        JsonNode altItems = alts.get("data").get("items");
        assertThat(altItems.size()).isBetween(1, 3);
        for (JsonNode a : altItems) assertThat(inWeek).doesNotContain(a.get("id").asLong());
        long newDish = altItems.get(0).get("id").asLong();

        JsonNode replaced = put(token, "/mealplan/drafts/" + draftId + "/slots/" + firstSlot, Map.of("dishId", newDish));
        assertThat(replaced.get("code").asInt()).as("换一道 msg=" + text(replaced, "msg")).isEqualTo(0);
        assertThat(replaced.toString()).contains("\"id\":" + newDish);
        JsonNode dup = put(token, "/mealplan/drafts/" + draftId + "/slots/" + firstSlot, Map.of("dishId", secondDish));
        assertThat(dup.get("code").asInt()).as("换成本周已有的菜应被拒").isNotEqualTo(0);

        JsonNode applied = post(token, "/mealplan/drafts/" + draftId + "/apply", null);
        assertThat(applied.get("code").asInt()).as("生成食集 msg=" + text(applied, "msg")).isEqualTo(0);
        List<Long> menuIds = new ArrayList<>();
        applied.get("data").get("menuIds").forEach(n -> menuIds.add(n.asLong()));
        assertThat(menuIds).hasSize(7);
        assertThat(applied.get("data").get("shoppingItemCount").asInt()).isGreaterThanOrEqualTo(0);

        JsonNode menu = get(token, "/menu/" + menuIds.get(0));
        assertThat(menu.get("code").asInt()).isEqualTo(0);
        assertThat(menu.toString()).contains("一周餐程");

        JsonNode after = get(token, "/mealplan/drafts/" + draftId);
        assertThat(after.get("data").get("status").asText()).isEqualTo("APPLIED");
        JsonNode again = post(token, "/mealplan/drafts/" + draftId + "/apply", null);
        assertThat(again.get("code").asInt()).as("重复生成应被拒").isNotEqualTo(0);

        for (Long id : menuIds) delete(token, "/menu/" + id); // 自清理（seed 兜底）
    }

    @Test
    void D_指定周必须是周一_营养报告() {
        String token = loginAdmin();
        LocalDate notMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.TUESDAY));
        JsonNode bad = post(token, "/mealplan/drafts", Map.of("weekStart", notMonday.toString()));
        assertThat(bad.get("code").asInt()).as("非周一应被拒").isNotEqualTo(0);

        long memberId = get(token, "/member/me").get("data").get("id").asLong();
        JsonNode report = get(token, "/nutrition/report?memberId=" + memberId + "&range=week");
        assertThat(report.get("code").asInt()).as("报告 msg=" + text(report, "msg")).isEqualTo(0);
        JsonNode data = report.get("data");
        assertThat(data.get("radar").size()).isEqualTo(5);
        assertThat(LocalDate.parse(data.get("range").get("from").asText()).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(data.get("trend").get("key").asText()).isEqualTo("sodium");
        assertThat(data.get("member").get("memberId").asLong()).isEqualTo(memberId);

        JsonNode month = get(token, "/nutrition/report?memberId=" + memberId + "&range=month");
        assertThat(month.get("code").asInt()).isEqualTo(0);
        assertThat(LocalDate.parse(month.get("data").get("range").get("from").asText()).getDayOfMonth()).isEqualTo(1);
        JsonNode badRange = get(token, "/nutrition/report?memberId=" + memberId + "&range=year");
        assertThat(badRange.get("code").asInt()).isNotEqualTo(0);
    }

    // ===================== HTTP helper（同 GuduE2ECoverageTest） =====================

    private JsonNode waitDone(String token, long jobId) throws InterruptedException {
        JsonNode last = null;
        for (int i = 0; i < 50; i++) {
            last = get(token, "/vision/jobs/" + jobId);
            assertThat(last.get("code").asInt()).isEqualTo(0);
            if (!"PENDING".equals(last.get("data").get("status").asText())) break;
            Thread.sleep(100);
        }
        assertThat(last.get("data").get("status").asText()).as("识别应完成").isEqualTo("DONE");
        return last.get("data");
    }

    private String loginAdmin() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "admin123");
        JsonNode r = post(null, "/auth/login", body);
        assertThat(r.get("code").asInt()).as("登录应成功").isEqualTo(0);
        return r.get("data").get("token").asText();
    }

    private JsonNode post(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.POST, body);
    }

    private JsonNode get(String token, String path) {
        return exchange(token, path, HttpMethod.GET, null);
    }

    private JsonNode put(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.PUT, body);
    }

    private JsonNode delete(String token, String path) {
        return exchange(token, path, HttpMethod.DELETE, null);
    }

    private JsonNode exchange(String token, String path, HttpMethod method, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("Authorization", token);
        try {
            ResponseEntity<String> resp = http.exchange(path, method, new HttpEntity<>(body, h), String.class);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException(method + " " + path + " 失败: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }
}
