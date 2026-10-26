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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 越权套件（multi-tenant-family G.1）：两个互不相干的家庭 A / B，B 对 A 的每类家庭域资源执行读 / 写 / 删，
 * 断言「拿不到、改不动、删不掉」（跨家庭统一 404 或读为空，A 的数据保持不变）；公共菜谱 B 可读不可写、可复制。
 *
 * <p>账号由超管经 POST /account 代建（每个新账号自动建「我的家」并成为 OWNER）；手机号 1390000xxxx、
 * 家庭 / 数据名称 E2E 前缀，由 e2e-seed.sql 兜底清理，可重复跑。
 * <p>跑法：SSH 隧道连 staging 后 {@code DB_HOST=127.0.0.1 REDIS_HOST=127.0.0.1 ./mvnw test -Dtest='GuduE2E*'}。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:e2e-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:e2e-teardown.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class GuduE2ETenantIsolationTest {

    private static final String PASSWORD = "e2ePass123";
    private static final long DISH_PUBLIC = 1L;     // 番茄炒蛋（V77 后在公共菜谱库）
    private static final long ING_TOMATO = 1L;
    private static final long UNIT_G = 20L;

    @Autowired
    private TestRestTemplate http;
    private final ObjectMapper om = new ObjectMapper();

    /** 一个家庭里的登录身份。 */
    record Who(String token, long accountId, long familyId, long profileId) {
    }

    // ===================================================================== 用例

    @Test
    void 跨家庭_菜单_库存_采购_草稿_私有菜谱_成员档案_全部不可见不可改不可删() {
        Who a = newFamily("13900000101", "E2E甲");
        Who b = newFamily("13900000102", "E2E乙");
        assertThat(a.familyId()).isNotEqualTo(b.familyId());

        // ---------- A 造数据 ----------
        long menuId = okLong(post(a, "/menu", Map.of(
                "menu", Map.of("name", "E2E甲的食集", "servingCount", 2),
                "dishes", List.of(Map.of("dishId", DISH_PUBLIC, "servingFactor", 1)))));
        long listId = okLong(post(a, "/shopping/generate", Map.of("sourceType", "dish", "sourceIds", List.of(DISH_PUBLIC))));
        ok(put(a, "/pantry/" + ING_TOMATO + "/level", Map.of("level", "LOW", "note", "E2E甲")));
        long draftId = okLong(post(a, "/dish/draft", Map.of("name", "E2E甲的草稿",
                "ingredients", List.of(), "steps", List.of())));
        long privateDish = okLong(post(a, "/dish", Map.of(
                "dish", Map.of("name", "E2E甲的私房菜", "difficulty", 1, "cookTime", 5),
                "ingredients", List.of(Map.of("ingredientId", ING_TOMATO, "amount", 100, "unitId", UNIT_G)),
                "steps", List.of(Map.of("seq", 1, "text", "秘方")),
                "cuisineIds", List.of(), "tagIds", List.of(), "categoryIds", List.of())));
        // 健康档案 + 不满 14 周岁：先单独同意（mini-release-compliance B.2，否则 428）
        ok(post(a, "/consent/sensitive", Map.of("docTypes", List.of("sensitive_health", "child_info"), "channel", "e2e")));
        long babyId = okLong(post(a, "/member", Map.of("name", "E2E甲家宝宝",
                "healthProfile", Map.of("height", 80, "weight", 10, "age", 1, "gender", "female"))));

        // ---------- B：食集 ----------
        assertDenied(get(b, "/menu/" + menuId), "读别家食集");
        assertDenied(put(b, "/menu/" + menuId + "/dish/" + DISH_PUBLIC + "/note", Map.of("note", "B改的")), "改别家食集备注");
        assertDenied(post(b, "/menu", Map.of("menu", Map.of("id", menuId, "name", "E2E被B改名"), "dishes", List.of())),
                "整单覆盖别家食集");
        delete(b, "/menu/" + menuId + "/dish/" + DISH_PUBLIC);
        delete(b, "/menu/" + menuId);
        assertDenied(get(b, "/menu/" + menuId + "/prep"), "读别家备菜清单");
        assertDenied(post(b, "/menu/" + menuId + "/together/items", Map.of("customName", "B加的菜")), "往别家食集加菜");
        JsonNode aMenu = ok(get(a, "/menu/" + menuId));
        assertThat(aMenu.get("menu").get("name").asText()).isEqualTo("E2E甲的食集");
        assertThat(aMenu.get("dishes")).hasSize(1);
        assertThat(aMenu.get("dishes").get(0).path("note").asText("")).isNotEqualTo("B改的");
        assertThat(listIds(ok(get(b, "/menu?pageNum=1&pageSize=50")))).doesNotContain(menuId);

        // ---------- B：采购清单 ----------
        assertDenied(get(b, "/shopping/" + listId), "读别家采购清单");
        put(b, "/shopping/" + listId + "/name", Map.of("name", "E2E被B改名"));
        delete(b, "/shopping/" + listId);
        assertThat(ok(get(a, "/shopping/" + listId)).path("name").asText("")).isNotEqualTo("E2E被B改名");

        // ---------- B：余量（按食材 id 的家庭内资源：B 改的是自己家那一行） ----------
        ok(put(b, "/pantry/" + ING_TOMATO + "/level", Map.of("level", "OUT", "note", "E2E乙")));
        delete(b, "/pantry/" + ING_TOMATO + "/level");
        assertThat(ok(get(a, "/pantry/item?ingredientId=" + ING_TOMATO)).get("level").asText()).isEqualTo("LOW");

        // ---------- B：菜谱草稿 ----------
        assertDenied(get(b, "/dish/draft/" + draftId), "读别家草稿");
        delete(b, "/dish/draft/" + draftId);
        assertThat(ok(get(a, "/dish/draft/" + draftId)).get("name").asText()).isEqualTo("E2E甲的草稿");

        // ---------- B：私有菜谱 ----------
        assertDenied(get(b, "/dish/" + privateDish), "读别家私有菜谱");
        assertDenied(get(b, "/dish/" + privateDish + "/nutrition?serving=1"), "读别家私有菜谱营养");
        JsonNode searchB = ok(post(b, "/dish/search", Map.of("keyword", "E2E甲的私房菜", "pageNum", 1, "pageSize", 10)));
        assertThat(searchB.get("total").asInt()).as("搜索不出别家私有菜谱").isZero();
        assertDenied(put(b, "/dish", Map.of("dish", Map.of("id", privateDish, "name", "E2E被B改名"),
                "ingredients", List.of(), "steps", List.of())), "改别家私有菜谱");
        assertDenied(delete(b, "/dish/" + privateDish), "删别家私有菜谱");
        assertDenied(post(b, "/dish/" + privateDish + "/fork", null), "复制别家私有菜谱");
        assertDenied(post(b, "/menu", Map.of("menu", Map.of("name", "E2E乙偷菜"),
                "dishes", List.of(Map.of("dishId", privateDish, "servingFactor", 1)))), "把别家私有菜谱放进自己食集");
        assertDenied(post(b, "/shopping/generate", Map.of("sourceType", "dish", "sourceIds", List.of(privateDish))),
                "按别家私有菜谱生成采购");
        assertThat(ok(get(a, "/dish/" + privateDish)).get("dish").get("name").asText()).isEqualTo("E2E甲的私房菜");

        // ---------- B：成员档案 ----------
        JsonNode bMembers = ok(get(b, "/member?pageNum=1&pageSize=100"));
        assertThat(bMembers.get("records").toString()).doesNotContain("E2E甲家宝宝").doesNotContain("E2E甲");
        assertThat(bMembers.get("records").toString()).doesNotContain("\"phone\"").doesNotContain("openid");
        assertDenied(put(b, "/member", Map.of("id", babyId, "name", "E2E被B改名")), "改别家成员档案");
        assertDenied(delete(b, "/member/" + babyId), "删别家成员档案");
        assertDenied(post(b, "/member/current?memberId=" + babyId, null), "切换为别家成员");
        assertDenied(get(b, "/member/" + babyId + "/permissions"), "读别家成员权限");
        assertThat(get(b, "/member/" + babyId + "/nutrition-target").get("data").isNull())
                .as("别家成员营养目标不可读").isTrue();
        assertThat(ok(get(a, "/member/" + babyId + "/nutrition-target")).get("calorieTarget").asInt()).isPositive();

        // ---------- B：家庭本身 ----------
        assertDenied(post(b, "/family/current", Map.of("familyId", a.familyId())), "切换到未加入的家庭");
        assertDenied(post(b, "/family/" + a.familyId() + "/invitations", null), "给别家生成邀请");
        assertDenied(delete(b, "/family/" + a.familyId()), "解散别家");
        assertDenied(delete(b, "/family/" + a.familyId() + "/members/" + a.accountId()), "移除别家成员");
        assertThat(ok(get(b, "/family/current")).get("id").asLong()).as("当前家庭不变").isEqualTo(b.familyId());
    }

    @Test
    void 公共菜谱_所有家庭可读_普通用户不可改不可删_可复制到我家() {
        Who b = newFamily("13900000103", "E2E丙");
        ok(get(b, "/dish/" + DISH_PUBLIC));
        JsonNode upd = put(b, "/dish", Map.of("dish", Map.of("id", DISH_PUBLIC, "name", "E2E被改的公共菜"),
                "ingredients", List.of(), "steps", List.of()));
        assertThat(upd.get("code").asInt()).as("普通用户改公共菜谱 403").isEqualTo(403);
        assertThat(delete(b, "/dish/" + DISH_PUBLIC).get("code").asInt()).isEqualTo(403);
        assertThat(ok(get(b, "/dish/" + DISH_PUBLIC)).get("dish").get("name").asText()).isEqualTo("番茄炒蛋");

        long forked = okLong(post(b, "/dish/" + DISH_PUBLIC + "/fork", null));
        JsonNode copy = ok(get(b, "/dish/" + forked)).get("dish");
        assertThat(copy.get("familyId").asLong()).isEqualTo(b.familyId());
        assertThat(copy.get("sourceDishId").asLong()).isEqualTo(DISH_PUBLIC);
        assertThat(ok(get(b, "/dish/" + forked)).get("ingredients")).hasSize(2);
        // 复制件改名只影响本家
        ok(put(b, "/dish", Map.of("dish", Map.of("id", forked, "name", "E2E丙的番茄炒蛋"),
                "ingredients", List.of(), "steps", List.of(), "cuisineIds", List.of(), "tagIds", List.of(),
                "categoryIds", List.of())));
        Who other = newFamily("13900000104", "E2E丁");
        assertDenied(get(other, "/dish/" + forked), "别家看不到复制件");
        delete(b, "/dish/" + forked);
    }

    @Test
    void 被移出家庭后_原会话访问家庭数据_403_FAMILY_ACCESS_DENIED() {
        Who owner = newFamily("13900000105", "E2E主理人");
        Who x = newFamily("13900000106", "E2E被移出者");
        String code = ok(post(owner, "/family/" + owner.familyId() + "/invitations", null)).get("code").asText();
        JsonNode joined = ok(post(x, "/family/join", Map.of("code", code)));
        assertThat(joined.get("familyId").asLong()).isEqualTo(owner.familyId());
        ok(get(x, "/menu?pageNum=1&pageSize=5"));

        ok(delete(owner, "/family/" + owner.familyId() + "/members/" + x.accountId()));

        JsonNode denied = get(x, "/menu?pageNum=1&pageSize=5");
        assertThat(denied.get("code").asInt()).isEqualTo(403);
        assertThat(denied.path("data").path("reason").asText("")).isEqualTo("FAMILY_ACCESS_DENIED");
        // 会话家庭已清：下一次请求自动落回自己的家
        ok(get(x, "/menu?pageNum=1&pageSize=5"));
        assertThat(ok(get(x, "/family/current")).get("id").asLong()).isEqualTo(x.familyId());
    }

    @Test
    void 聚餐_非本家登录用户未凭邀请加入_不能按id访问食集() {
        Who a = newFamily("13900000107", "E2E聚餐甲");
        Who b = newFamily("13900000108", "E2E聚餐乙");
        long menuId = okLong(post(a, "/menu", Map.of("menu", Map.of("name", "E2E聚餐食集", "servingCount", 2),
                "dishes", List.of())));
        assertDenied(get(b, "/menu/" + menuId + "/together"), "未加入的别家用户读聚餐清单");
        // 凭邀请加入后可以参与这一个食集
        String token = ok(post(a, "/menu/" + menuId + "/invite", null)).get("token").asText();
        ok(post(b, "/invite/" + token + "/join", Map.of("nickname", "乙")));
        ok(get(b, "/menu/" + menuId + "/together"));
        ok(post(b, "/menu/" + menuId + "/together/items", Map.of("customName", "E2E乙带的菜")));
        // 但仍然不能读该食集所在家庭的其他数据
        assertDenied(get(b, "/menu/" + menuId), "加入聚餐不等于加入家庭");
    }

    @Test
    void 聚餐_加入必须持有有效凭证_撤销刷新后旧凭证失效_凭证不跨食集() {
        Who a = newFamily("13900000109", "E2E凭证甲");
        Who b = newFamily("13900000110", "E2E凭证乙");
        long menu1 = okLong(post(a, "/menu", Map.of("menu", Map.of("name", "E2E凭证食集1", "servingCount", 2),
                "dishes", List.of())));
        long menu2 = okLong(post(a, "/menu", Map.of("menu", Map.of("name", "E2E凭证食集2", "servingCount", 2),
                "dishes", List.of())));
        // 无凭证 / 伪造凭证：不能加入，也读不到
        assertDenied(post(b, "/invite/not-a-real-token/join", Map.of("nickname", "乙")), "伪造凭证加入");
        assertDenied(get(b, "/menu/" + menu1 + "/together"), "无凭证读聚餐");
        assertDenied(get(b, "/menu/" + menu1 + "/together/dishes?keyword=番茄"), "无凭证搜菜");
        assertDenied(delete(b, "/menu/" + menu1 + "/invite"), "别家用户撤销邀请");

        // 撤销后凭证失效
        String tok1 = ok(post(a, "/menu/" + menu1 + "/invite", null)).get("token").asText();
        ok(delete(a, "/menu/" + menu1 + "/invite"));
        assertDenied(post(b, "/invite/" + tok1 + "/join", Map.of("nickname", "乙")), "已撤销凭证加入");
        assertDenied(get(b, "/invite/" + tok1), "已撤销凭证查看");

        // 刷新后旧凭证作废、新凭证可用；凭证 1 只能进食集 1
        String tok1b = ok(post(a, "/menu/" + menu1 + "/invite", null)).get("token").asText();
        assertThat(tok1b).isNotEqualTo(tok1);
        assertDenied(post(b, "/invite/" + tok1 + "/join", Map.of("nickname", "乙")), "刷新前的旧凭证");
        ok(post(b, "/invite/" + tok1b + "/join", Map.of("nickname", "乙")));
        ok(get(b, "/menu/" + menu1 + "/together"));
        ok(get(b, "/menu/" + menu1 + "/together/dishes?keyword=番茄"));
        assertDenied(get(b, "/menu/" + menu2 + "/together"), "拿食集1的凭证读食集2的聚餐");
        assertDenied(post(b, "/menu/" + menu2 + "/together/items", Map.of("customName", "E2E越权菜")),
                "拿食集1的凭证往食集2加菜");
        // 已加入者撤销凭证后仍可凭加入记录继续参与（但不能再拉别人）
        ok(delete(a, "/menu/" + menu1 + "/invite"));
        ok(get(b, "/menu/" + menu1 + "/together"));
    }

    // ===================================================================== helpers

    private Who newFamily(String phone, String nickname) {
        String admin = loginToken("admin", "admin123");
        JsonNode created = exchange(admin, "/account", HttpMethod.POST,
                Map.of("phone", phone, "password", PASSWORD, "nickname", nickname));
        assertThat(created.get("code").asInt()).as("超管代建账号 msg=" + created.path("msg").asText()).isZero();
        JsonNode login = exchange(null, "/auth/login", HttpMethod.POST, Map.of("username", phone, "password", PASSWORD));
        assertThat(login.get("code").asInt()).as("登录 msg=" + login.path("msg").asText()).isZero();
        JsonNode d = login.get("data");
        assertThat(d.get("families")).as("新账号自动建「我的家」").hasSize(1);
        return new Who(d.get("token").asText(), d.get("accountId").asLong(), d.get("currentFamilyId").asLong(),
                d.get("currentMemberId").asLong());
    }

    private String loginToken(String user, String pw) {
        JsonNode r = exchange(null, "/auth/login", HttpMethod.POST, Map.of("username", user, "password", pw));
        assertThat(r.get("code").asInt()).isZero();
        return r.get("data").get("token").asText();
    }

    /** 越权：要么 404 / 403 业务码，要么成功但读不到数据（data 为空）。不允许 code=0 且带回数据。 */
    private static void assertDenied(JsonNode r, String what) {
        int code = r.get("code").asInt();
        boolean empty = r.get("data") == null || r.get("data").isNull();
        assertThat(code != 0 || empty).as(what + " 应被拒绝，实际 " + r).isTrue();
        if (code != 0) assertThat(code).as(what + " 业务码").isIn(1, 403, 404);
    }

    private static List<Long> listIds(JsonNode page) {
        List<Long> out = new java.util.ArrayList<>();
        page.get("records").forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    private static JsonNode ok(JsonNode r) {
        assertThat(r.get("code").asInt()).as("应成功 msg=" + r.path("msg").asText()).isZero();
        return r.get("data");
    }

    private static long okLong(JsonNode r) {
        return ok(r).asLong();
    }

    private JsonNode get(Who w, String path) {
        return exchange(w.token(), path, HttpMethod.GET, null);
    }

    private JsonNode post(Who w, String path, Object body) {
        return exchange(w.token(), path, HttpMethod.POST, body);
    }

    private JsonNode put(Who w, String path, Object body) {
        return exchange(w.token(), path, HttpMethod.PUT, body);
    }

    private JsonNode delete(Who w, String path) {
        return exchange(w.token(), path, HttpMethod.DELETE, null);
    }

    private JsonNode exchange(String token, String path, HttpMethod method, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (body != null) h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("Authorization", token);
        try {
            ResponseEntity<String> resp = http.exchange(path, method, new HttpEntity<>(body, h), String.class);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException(method + " " + path + " 失败: " + e.getMessage(), e);
        }
    }
}
