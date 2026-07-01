package com.gudu.xsd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全链路 E2E 集成测试：@SpringBootTest RANDOM_PORT 起真 Tomcat，TestRestTemplate 走真 HTTP。
 * 连独立测试库 gudu_test + 真 Redis(16379)。Sa-Token 真登录拿 token，后续请求带 Authorization。
 *
 * <p>种子(gudu_test 已灌 V01-V20 + demo)：
 * <ul>
 *   <li>user admin / admin123</li>
 *   <li>member 1 张爸爸(掌勺 role=32) / member 2 张妈妈(普通成员 role=34)</li>
 *   <li>ingredient 1 番茄(蔬菜/24, unit g/20) / 2 鸡蛋(蛋类/27)</li>
 *   <li>dish 1 番茄炒蛋(含 ingredient 1 300g + 2 180g)</li>
 *   <li>nutrition_metric 1 calorie(19/100g 番茄, 144/100g 鸡蛋)</li>
 * </ul>
 *
 * <p>每个 @Test 前由 e2e-seed.sql 物理清理动态业务表，保证用例隔离。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:e2e-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GuduE2EFlowTest {

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper om = new ObjectMapper();

    /** 静态种子 id（gudu_test 由 V01-V20 + demo 灌入，固定不变）。 */
    private static final long ADMIN_USER_ID = 1L;
    private static final long MEMBER_CHEF = 1L;        // 张爸爸 掌勺(32)
    private static final long MEMBER_NORMAL = 2L;       // 张妈妈 普通成员(34)
    private static final long DISH_FANQIE_CHAODAN = 1L; // 番茄炒蛋
    private static final long ING_TOMATO = 1L;          // 番茄
    private static final long UNIT_G = 20L;             // g
    private static final long CAT_VEGETABLE = 24L;      // 蔬菜

    /** 后端 context-path 前缀（application.yml: server.servlet.context-path=/gudu）。TestRestTemplate 走真 Tomcat，必须带。 */
    private static final String CTX = "/gudu";

    /** 每个测试前真登录拿新 token（Sa-Token token-style=uuid，登录即发新券）。 */
    private String loginAdmin() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "admin123");
        JsonNode r = post("/auth/login", body);
        assertThat(r.get("code").asInt()).isEqualTo(0);
        String token = r.get("data").get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    // ---------------- 5 场景 ----------------

    /** 场景0（V29 合并）：手机号 member 登录成功 + session.currentMemberId = loginId（合并核心）。 */
    @Test
    void 合并_手机号member登录成功_登录即定就餐成员() {
        // 用 member 1(张爸爸) 的手机号 13800000001 / 密码 chef123 登录（e2e-seed 兜底种子）
        Map<String, String> body = new HashMap<>();
        body.put("username", "13800000001");
        body.put("password", "chef123");
        JsonNode r = post("/auth/login", body);
        assertThat(r.get("code").asInt())
                .as("手机号登录应成功 msg=" + text(r, "msg")).isEqualTo(0);
        String token = r.get("data").get("token").asText();
        assertThat(token).as("应返回 token").isNotBlank();
        // nickname = member.name（张爸爸）
        assertThat(r.get("data").get("nickname").asText()).isEqualTo("张爸爸");

        // 合并核心：登录即定就餐成员，session.currentMemberId == loginId(member.id=1)
        JsonNode cur = get(token, "/member/current");
        assertThat(cur.get("code").asInt()).isEqualTo(0);
        // MEMBER_CHEF=1，登录即定就餐成员后应等于 1，无需再调 /member/current
        assertThat(cur.get("data").asLong())
                .as("登录即定就餐成员：currentMemberId 应=member.id(1)")
                .isEqualTo(MEMBER_CHEF);
    }

    /** 场景1：登录 + 设就餐成员 + 建周计划 + 挂菜 + 重复挂菜去重。 */
    @Test
    void 排菜_登录设成员建计划挂菜_重复挂菜返回去重() {
        String token = loginAdmin();

        // 设当前就餐成员（掌勺）
        JsonNode r1 = post(token, "/member/current?memberId=" + MEMBER_CHEF, null);
        assertThat(r1.get("code").asInt()).isEqualTo(0);

        // 建本周计划
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        Map<String, Object> planReq = new HashMap<>();
        planReq.put("weekStart", monday.toString());
        planReq.put("name", "E2E测试周计划");
        JsonNode r2 = post(token, "/mealplan", planReq);
        assertThat(r2.get("code").asInt()).isEqualTo(0);
        long planId = r2.get("data").asLong();
        assertThat(planId).isPositive();

        // 挂番茄炒蛋（周一午餐）
        Map<String, Object> itemReq = new HashMap<>();
        itemReq.put("date", monday.toString());
        itemReq.put("meal", "午餐");
        itemReq.put("dishId", DISH_FANQIE_CHAODAN);
        itemReq.put("servingFactor", 1);
        JsonNode r3 = post(token, "/mealplan/" + planId + "/item", itemReq);
        assertThat(r3.get("code").asInt()).isEqualTo(0);
        long itemId = r3.get("data").get("itemId").asLong();
        assertThat(itemId).isPositive();
        // 首次挂菜，duplicates 应为空
        assertThat(r3.get("data").get("duplicates").isArray()).isTrue();
        assertThat(r3.get("data").get("duplicates").size()).isZero();

        // 再挂同菜同日同餐 → 唯一约束 uk_plan_date_meal_dish 触发
        JsonNode r4 = post(token, "/mealplan/" + planId + "/item", itemReq);
        // 唯一约束冲突 → R.fail（code≠0）或 service 层抛错。两种都算「去重生效」。
        int code4 = r4.get("code").asInt();
        assertThat(code4)
                .as("重复挂菜应被去重拦截：code≠0，实际=" + code4 + " msg=" + text(r4, "msg"))
                .isNotZero();
    }

    /** 场景2（redesign）：从菜品生成采购草稿 → 验证参考克数 → 用户填采购量+采购单位 → 验证保存。 */
    @Test
    void 采购_从菜品生成清单_用户填采购量保存() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 从「番茄炒蛋」(dish=1) 直接生成采购草稿
        Map<String, Object> genReq = Map.of("sourceType", "dish", "sourceIds", List.of(DISH_FANQIE_CHAODAN));
        JsonNode gen = post(token, "/shopping/generate", genReq);
        assertThat(gen.get("code").asInt()).isEqualTo(0);
        long listId = gen.get("data").asLong();
        assertThat(listId).isPositive();

        // 查清单详情：番茄参考克 = 300g（菜谱用量），purchase_amount/unit 草稿态为 null
        JsonNode detail = get(token, "/shopping/" + listId);
        assertThat(detail.get("code").asInt()).isEqualTo(0);
        JsonNode data = detail.get("data");
        assertThat(data.get("items").isArray()).isTrue();
        JsonNode tomato = null;
        for (JsonNode it : data.get("items")) {
            if ("番茄".equals(it.get("ingredientName").asText())) {
                tomato = it;
                break;
            }
        }
        assertThat(tomato).as("采购清单应含番茄").isNotNull();
        assertThat(tomato.get("referenceGrams").asDouble())
                .as("番茄炒蛋番茄用量参考克 = 300g")
                .isEqualTo(300.0);
        // 草稿态：采购量/单位未填
        assertThat(tomato.get("purchaseAmount").isNull()).as("草稿态采购量应为空").isTrue();

        // 查采购单位字典 group=purchase_unit，拿「个」id
        JsonNode dictResp = get(token, "/dict?group=purchase_unit&pageNum=1&pageSize=20");
        long unitGe = -1L;
        for (JsonNode d : dictResp.get("data").get("records")) {
            if ("个".equals(d.get("name").asText())) { unitGe = d.get("id").asLong(); break; }
        }
        assertThat(unitGe).as("采购单位字典应含「个」").isPositive();

        // 用户填采购量 3 个
        long tomatoItemId = tomato.get("id").asLong();
        Map<String, Object> upd = Map.of("purchaseAmount", 3, "purchaseUnitId", unitGe);
        JsonNode updResp = put(token, "/shopping/item/" + tomatoItemId, upd);
        assertThat(updResp.get("code").asInt()).isEqualTo(0);

        // 重新查清单：采购量=3、采购单位中文=「个」
        JsonNode detail2 = get(token, "/shopping/" + listId);
        JsonNode tomato2 = null;
        for (JsonNode it : detail2.get("data").get("items")) {
            if ("番茄".equals(it.get("ingredientName").asText())) { tomato2 = it; break; }
        }
        assertThat(tomato2.get("purchaseAmount").asDouble()).isEqualTo(3.0);
        assertThat(tomato2.get("purchaseUnitName").asText()).isEqualTo("个");

        // grouped 分区仍包含蔬菜品类（参考分区保留）
        assertThat(detail2.get("data").get("grouped").has(Integer.toString((int) CAT_VEGETABLE)))
                .as("按品类分区应含「蔬菜」").isTrue();
    }

    /** 场景3：录临期库存 → 手动触发临期扫描 → 掌勺成员收到 expiry 通知。 */
    @Test
    void 通知_录临期库存触发扫描_掌勺成员收到临期通知() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 录库存：番茄，过期日 = 明天（落在 3 天临期窗口内）
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Map<String, Object> pantryReq = new HashMap<>();
        pantryReq.put("ingredientId", ING_TOMATO);
        pantryReq.put("amount", 500);
        pantryReq.put("unitId", UNIT_G);
        pantryReq.put("expireDate", tomorrow.toString());
        pantryReq.put("lowThreshold", 100);
        JsonNode rp = post(token, "/pantry", pantryReq);
        assertThat(rp.get("code").asInt()).isEqualTo(0);

        // 手动触发临期扫描（不等 @Scheduled）
        JsonNode scan = post(token, "/notification/scan-expiring?days=3", null);
        assertThat(scan.get("code").asInt())
                .as("扫描不应报错 msg=" + text(scan, "msg")).isEqualTo(0);
        int sent = scan.get("data").get("sent").asInt();
        assertThat(sent).as("至少扫到 1 条临期库存").isGreaterThanOrEqualTo(1);

        // 查通知列表（当前就餐成员=掌勺，应收到 expiry）
        JsonNode list = get(token, "/notification");
        assertThat(list.get("code").asInt()).isEqualTo(0);
        boolean hasExpiry = false;
        for (JsonNode n : list.get("data")) {
            if ("expiry".equals(n.get("type").asText())) {
                hasExpiry = true;
                break;
            }
        }
        assertThat(hasExpiry).as("掌勺成员应收到 type=expiry 的临期通知").isTrue();
    }

    /** 场景4：提交饮食记录（番茄炒蛋 1 份）→ 营养汇总 calorie > 0。 */
    @Test
    void 饮食_记番茄炒蛋1份_营养汇总calorie大于0() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 提交当天日志：1 份番茄炒蛋
        LocalDate today = LocalDate.now();
        Map<String, Object> item = Map.of(
                "dishId", DISH_FANQIE_CHAODAN,
                "amount", 1,
                "servingFactor", 1);
        Map<String, Object> logReq = new HashMap<>();
        logReq.put("date", today.toString());
        logReq.put("note", "E2E 测试");
        logReq.put("items", java.util.List.of(item));

        JsonNode rs = post(token, "/dailylog", logReq);
        assertThat(rs.get("code").asInt()).isEqualTo(0);
        long logId = rs.get("data").asLong();
        assertThat(logId).isPositive();

        // 查营养汇总：metricId=1(calorie) 应 > 0
        JsonNode nut = get(token, "/dailylog/" + logId + "/nutrition");
        assertThat(nut.get("code").asInt()).isEqualTo(0);
        JsonNode map = nut.get("data");
        assertThat(map).isNotNull();
        // 番茄炒蛋：番茄300g(19kcal/100g)+鸡蛋180g(144kcal/100g) ≈ 57+259 ≈ 316 kcal，必然 > 0
        // metricId=1 = calorie。JSON key 是字符串 "1"
        assertThat(map.has("1")).as("营养汇总应含 calorie(metricId=1)").isTrue();
        double calorie = map.get("1").asDouble();
        assertThat(calorie)
                .as("番茄炒蛋 1 份 calorie 应 > 0，实际=" + calorie)
                .isGreaterThan(0);
    }

    /** 场景5：设普通成员(无 dish.create 权限)为 currentMember → 录菜被权限切面拒绝。 */
    @Test
    void 权限_普通成员录菜_被切面拒绝返回失败() {
        String token = loginAdmin();
        // 设普通成员（role=34，无 dish.create）
        post(token, "/member/current?memberId=" + MEMBER_NORMAL, null);

        // 尝试录新菜
        Map<String, Object> dish = new HashMap<>();
        dish.put("name", "E2E权限测试菜");
        dish.put("difficulty", 1);
        Map<String, Object> saveReq = new HashMap<>();
        saveReq.put("dish", dish);
        saveReq.put("ingredients", java.util.List.of());
        saveReq.put("steps", java.util.List.of());

        JsonNode r = post(token, "/dish", saveReq);
        // 权限切面抛 BizException("无此功能权限") → R.fail(code=1, msg 含「权限」)
        int code = r.get("code").asInt();
        assertThat(code)
                .as("普通成员录菜应被拒绝：code≠0，实际=" + code)
                .isNotZero();
        String msg = text(r, "msg");
        assertThat(msg).containsAnyOf("权限", "无此");
    }

    /** 场景6（AI）：营养补全 番茄（带 ingredientId=1）→ 6 项指标落到 ingredient_nutrition。 */
    @Test
    void AI_营养补全番茄_六项指标落库() {
        String token = loginAdmin();
        // 设掌勺成员（role=32 含 ai.use 权限）
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        Map<String, Object> req = Map.of("name", "番茄", "ingredientId", ING_TOMATO);
        JsonNode r = post(token, "/ai/nutrition/fill", req);
        assertThat(r.get("code").asInt())
                .as("营养补全应成功 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode data = r.get("data");
        assertThat(data.get("source").asText()).isEqualTo("mock");
        // 响应含 6 项指标
        assertThat(data.get("nutrition").isArray()).isTrue();
        assertThat(data.get("nutrition").size()).isEqualTo(6);
        // calorie(metricId=1) = 19
        int cal = -1;
        for (JsonNode n : data.get("nutrition")) {
            if (n.get("metricId").asLong() == 1L) { cal = n.get("value").asInt(); break; }
        }
        assertThat(cal).as("番茄 calorie 应=19").isEqualTo(19);

        // 验证 ingredient_nutrition 真落了 6 项：GET /ingredient/1/nutrition
        JsonNode nut = get(token, "/ingredient/" + ING_TOMATO + "/nutrition");
        assertThat(nut.get("code").asInt()).isEqualTo(0);
        JsonNode map = nut.get("data");
        assertThat(map.has("1")).as("落库应含 calorie").isTrue();
        assertThat(map.has("2")).as("落库应含 protein").isTrue();
        assertThat(map.has("3")).as("落库应含 fat").isTrue();
        assertThat(map.has("4")).as("落库应含 carb").isTrue();
        assertThat(map.has("5")).as("落库应含 sugar").isTrue();
        assertThat(map.has("6")).as("落库应含 gi").isTrue();
    }

    /** 场景7（AI）：菜单推荐 DAY scope → 候选 ≤ 1 组、每组 totalPrice ≤ budget。 */
    @Test
    void AI_菜单推荐_DAY候选受限且不超预算() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 候选池：番茄炒蛋(dish=1，含营养)。budget 给一个能容纳的值。
        Map<String, Object> req = new HashMap<>();
        req.put("memberId", MEMBER_CHEF);
        req.put("budget", 100);   // 番茄炒蛋参考价远低于 100
        req.put("scope", "DAY");
        JsonNode r = post(token, "/ai/menu/recommend", req);
        assertThat(r.get("code").asInt())
                .as("菜单推荐应成功 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode arr = r.get("data");
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).as("DAY 至多 1 组候选").isLessThanOrEqualTo(1);
        // 每组 totalPrice ≤ budget
        for (JsonNode g : arr) {
            double total = g.get("totalPrice").asDouble();
            assertThat(total).as("候选总价不超预算 100").isLessThanOrEqualTo(100.0);
            // source = mock
            assertThat(g.get("source").asText()).isEqualTo("mock");
            // dishes 非空
            assertThat(g.get("dishes").isArray()).isTrue();
            assertThat(g.get("dishes").size()).isPositive();
        }
    }

    /** 场景8：建菜单 + 关联番茄炒蛋 + 汇总总价/营养。 */
    @Test
    void 菜单_建菜单关联番茄炒蛋_汇总总价营养() {
        String token = loginAdmin();

        // 建菜单并关联番茄炒蛋（2 份）
        Map<String, Object> menu = new HashMap<>();
        menu.put("name", "E2E测试菜单");
        menu.put("servingCount", 2);
        Map<String, Object> md = new HashMap<>();
        md.put("dishId", DISH_FANQIE_CHAODAN);
        md.put("servingFactor", 2);
        Map<String, Object> saveReq = new HashMap<>();
        saveReq.put("menu", menu);
        saveReq.put("dishes", java.util.List.of(md));

        JsonNode r = post(token, "/menu", saveReq);
        assertThat(r.get("code").asInt())
                .as("建菜单应成功 msg=" + text(r, "msg")).isEqualTo(0);
        long menuId = r.get("data").asLong();
        assertThat(menuId).as("应返回菜单 id").isPositive();

        // 查详情：含 1 道关联菜
        JsonNode detail = get(token, "/menu/" + menuId);
        assertThat(detail.get("code").asInt()).isEqualTo(0);
        JsonNode d = detail.get("data");
        assertThat(d.get("menu").get("name").asText()).isEqualTo("E2E测试菜单");
        assertThat(d.get("dishes").isArray()).isTrue();
        assertThat(d.get("dishes").size()).isEqualTo(1);
        assertThat(d.get("dishes").get(0).get("dishId").asLong()).isEqualTo(DISH_FANQIE_CHAODAN);

        // 汇总：总价 > 0、营养含 calorie(metricId=1)
        JsonNode summary = get(token, "/menu/" + menuId + "/summary");
        assertThat(summary.get("code").asInt()).isEqualTo(0);
        JsonNode s = summary.get("data");
        assertThat(s.get("totalPrice").asDouble())
                .as("菜单总价应 > 0").isGreaterThan(0);
        assertThat(s.get("totalNutrition").has("1"))
                .as("菜单营养汇总应含 calorie(metricId=1)").isTrue();
        // 番茄炒蛋 2 份 calorie > 0
        assertThat(s.get("totalNutrition").get("1").asDouble()).isGreaterThan(0);

        // 清理：删除菜单
        JsonNode del = delete(token, "/menu/" + menuId);
        assertThat(del.get("code").asInt()).isEqualTo(0);
    }

    /** 场景9：member 分页查询，返回家庭成员列表（含张爸爸）。 */
    @Test
    void 成员_分页查询_返回家庭成员列表() {
        String token = loginAdmin();

        JsonNode r = get(token, "/member?pageNum=1&pageSize=20");
        assertThat(r.get("code").asInt()).isEqualTo(0);
        JsonNode data = r.get("data");
        assertThat(data.get("total").asLong()).as("至少 2 个成员").isGreaterThanOrEqualTo(2);
        assertThat(data.get("records").isArray()).isTrue();
        // 种子含 member 1 张爸爸
        boolean hasChef = false;
        for (JsonNode m : data.get("records")) {
            if ("张爸爸".equals(m.get("name").asText())) { hasChef = true; break; }
        }
        assertThat(hasChef).as("成员列表应含张爸爸").isTrue();
    }

    /** 场景10：dict 分页查 unit 组，返回字典项分页结构。 */
    @Test
    void 字典_分页查unit组_返回字典项() {
        String token = loginAdmin();

        JsonNode r = get(token, "/dict?group=unit&pageNum=1&pageSize=20");
        assertThat(r.get("code").asInt()).isEqualTo(0);
        JsonNode data = r.get("data");
        assertThat(data.get("total").asLong()).as("unit 字典至少 1 项").isGreaterThanOrEqualTo(1);
        assertThat(data.get("records").isArray()).isTrue();
        // 每项都在 unit 组
        for (JsonNode d : data.get("records")) {
            assertThat(d.get("dictGroup").asText()).isEqualTo("unit");
        }
    }

    /** 场景11：backup 全量导出，返回 15 张表的结构。 */
    @Test
    void 备份_全量导出_返回15张表结构() {
        String token = loginAdmin();

        JsonNode r = get(token, "/backup/export");
        assertThat(r.get("code").asInt())
                .as("备份导出应成功 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode data = r.get("data");
        assertThat(data.get("tableCount").asInt()).isEqualTo(15);
        assertThat(data.get("tables").isObject()).isTrue();
        // 关键业务表都应存在
        for (String t : java.util.List.of(
                "sys_dict", "user", "member", "ingredient", "dish", "menu")) {
            assertThat(data.get("tables").has(t))
                    .as("导出应含表 " + t).isTrue();
        }
    }

    // ---------------- HTTP 工具 ----------------

    /** POST（带 Authorization header）。 */
    private JsonNode post(String token, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("Authorization", token);
        try {
            String json = body == null ? "" : om.writeValueAsString(body);
            ResponseEntity<String> resp = http.exchange(CTX + path, HttpMethod.POST,
                    new HttpEntity<>(json, h), String.class);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException("POST " + path + " 失败: " + e.getMessage(), e);
        }
    }

    /** POST（不带 token，仅登录用）。 */
    private JsonNode post(String path, Object body) {
        return post(null, path, body);
    }

    /** GET（带 Authorization header）。 */
    private JsonNode get(String token, String path) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("Authorization", token);
        try {
            ResponseEntity<String> resp = http.exchange(CTX + path, HttpMethod.GET,
                    new HttpEntity<>(h), String.class);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException("GET " + path + " 失败: " + e.getMessage(), e);
        }
    }

    /** PUT（带 Authorization header + JSON body）。 */
    private JsonNode put(String token, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("Authorization", token);
        try {
            String json = body == null ? "" : om.writeValueAsString(body);
            ResponseEntity<String> resp = http.exchange(CTX + path, HttpMethod.PUT,
                    new HttpEntity<>(json, h), String.class);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException("PUT " + path + " 失败: " + e.getMessage(), e);
        }
    }

    /** DELETE（带 Authorization header）。 */
    private JsonNode delete(String token, String path) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("Authorization", token);
        try {
            ResponseEntity<String> resp = http.exchange(CTX + path, HttpMethod.DELETE,
                    new HttpEntity<>(h), String.class);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException("DELETE " + path + " 失败: " + e.getMessage(), e);
        }
    }

    /** 安全取 msg 文本（可能为 null）。 */
    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    /** 造一个排菜项请求体。 */
    private static Map<String, Object> item(LocalDate date, String meal, long dishId) {
        Map<String, Object> m = new HashMap<>();
        m.put("date", date.toString());
        m.put("meal", meal);
        m.put("dishId", dishId);
        m.put("servingFactor", 1);
        return m;
    }
}
