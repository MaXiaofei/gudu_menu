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
@Sql(scripts = "classpath:e2e-teardown.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
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

    /** 后端 context-path 前缀。
     *  注：@SpringBootTest(RANDOM_PORT) 内嵌 Tomcat 不应用 server.servlet.context-path，
     *  测试期实际是根 context，故 CTX 留空（请求直接打 /auth/login）。生产部署 context-path=/gudu 仍生效。 */
    private static final String CTX = "";

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

        // 查清单详情：番茄在清单中（V55：referenceGrams 停用不再填），purchase_amount/unit 草稿态为 null
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
        assertThat(tomato.get("referenceGrams").isNull())
                .as("V55 后 referenceGrams 不再写入（列保留停用）").isTrue();
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

    /** 场景3：e2e-seed.sql 已插「明天过期」的番茄批次 → 手动触发临期扫描 → 掌勺成员收到 expiry 通知。 */
    @Test
    void 通知_录临期库存触发扫描_掌勺成员收到临期通知() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 临期库存由 e2e-seed.sql 提供（V42 pantry 改 3 档后 POST /pantry 已删，
        //   临期通知扫描仍读 pantry 批次表 expire_date；种子插一条 CURDATE()+1 过期）。

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
        if (sent > 0) {
            assertThat(hasExpiry).as("有 sent 应收到 expiry 通知").isTrue();
        }
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

    /** 场景7（AI）：菜单推荐 DAY scope → 候选 ≤ 1 组（V55：预算链路已删）。 */
    @Test
    void AI_菜单推荐_DAY候选受限() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 候选池：番茄炒蛋(dish=1，含营养)
        Map<String, Object> req = new HashMap<>();
        req.put("memberId", MEMBER_CHEF);
        req.put("scope", "DAY");
        JsonNode r = post(token, "/ai/menu/recommend", req);
        assertThat(r.get("code").asInt())
                .as("菜单推荐应成功 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode arr = r.get("data");
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).as("DAY 至多 1 组候选").isLessThanOrEqualTo(1);
        for (JsonNode g : arr) {
            // source = mock
            assertThat(g.get("source").asText()).isEqualTo("mock");
            // dishes 非空
            assertThat(g.get("dishes").isArray()).isTrue();
            assertThat(g.get("dishes").size()).isPositive();
        }
    }

    /** 场景8：建菜单 + 关联番茄炒蛋 + 汇总营养（V55：价格链路已删）。 */
    @Test
    void 菜单_建菜单关联番茄炒蛋_汇总营养() {
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

        // 汇总：营养含 calorie(metricId=1)
        JsonNode summary = get(token, "/menu/" + menuId + "/summary");
        assertThat(summary.get("code").asInt()).isEqualTo(0);
        JsonNode s = summary.get("data");
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

    /** 场景12（V36 Plan A）：建菜单 → 整集做菜 → 每菜写 cooking_record。 */
    @Test
    void 做菜_整集做菜扣库存_写record() {
        String token = loginAdmin();

        // 建菜单关联番茄炒蛋（1 份）
        Map<String, Object> menu = new HashMap<>();
        menu.put("name", "E2E做菜测试菜单");
        menu.put("servingCount", 1);
        Map<String, Object> md = new HashMap<>();
        md.put("dishId", DISH_FANQIE_CHAODAN);
        md.put("servingFactor", 1);
        Map<String, Object> saveReq = new HashMap<>();
        saveReq.put("menu", menu);
        saveReq.put("dishes", java.util.List.of(md));

        JsonNode created = post(token, "/menu", saveReq);
        assertThat(created.get("code").asInt())
                .as("建菜单应成功 msg=" + text(created, "msg")).isEqualTo(0);
        long menuId = created.get("data").asLong();
        assertThat(menuId).as("应返回菜单 id").isPositive();

        // 整集做菜：聚合番茄炒蛋用量 → 扣 pantry → 写 cooking_record
        JsonNode cook = post(token, "/menu/" + menuId + "/cook", null);
        assertThat(cook.get("code").asInt())
                .as("整集做菜应成功 msg=" + text(cook, "msg")).isEqualTo(0);
        JsonNode data = cook.get("data");
        assertThat(data.get("menuId").asLong()).isEqualTo(menuId);
        // 每菜写一条 cooking_record（番茄炒蛋 1 条）
        assertThat(data.get("cookingRecordIds").isArray()).isTrue();
        assertThat(data.get("cookingRecordIds").size())
                .as("每菜一条 cooking_record").isEqualTo(1);

        // 清理
        delete(token, "/menu/" + menuId);
    }

    /**
     * 场景（feature-gaps-closure G1）：单菜直做全链路——弹窗数据 → 做菜（番茄用完 / 鸡蛋用了一些）
     * → 最近做过首条为该菜 → 库存档位变化（番茄 NONE 且保质期清除、鸡蛋 ENOUGH→LOW）→ 食记可见。
     * 种子：番茄 ENOUGH(明天到期)、鸡蛋 ENOUGH。
     */
    @Test
    void 单菜直做_做完_最近做过可见_库存档位回写() {
        String token = loginAdmin();

        JsonNode mats = get(token, "/cook/dish/" + DISH_FANQIE_CHAODAN + "/materials");
        assertThat(mats.get("code").asInt()).as("materials msg=" + text(mats, "msg")).isEqualTo(0);
        boolean hasTomato = false;
        for (JsonNode it : mats.get("data").get("items")) {
            if (it.get("ingredientId").asLong() == ING_TOMATO) {
                hasTomato = true;
                assertThat(it.get("level").asText()).isEqualTo("ENOUGH");
            }
        }
        assertThat(hasTomato).isTrue();

        Map<String, Object> body = new HashMap<>();
        body.put("servings", 1);
        body.put("usedUp", List.of(ING_TOMATO));
        body.put("partiallyUsed", List.of(2L));
        body.put("note", "E2E单菜直做");
        JsonNode cook = post(token, "/cook/dish/" + DISH_FANQIE_CHAODAN, body);
        assertThat(cook.get("code").asInt()).as("单菜直做 msg=" + text(cook, "msg")).isEqualTo(0);
        assertThat(cook.get("data").get("recordId").asLong()).isPositive();
        assertThat(cook.get("data").get("dishId").asLong()).isEqualTo(DISH_FANQIE_CHAODAN);

        // 最近做过首条 = 番茄炒蛋
        JsonNode recent = get(token, "/cookbook/recent?limit=10");
        assertThat(recent.get("code").asInt()).isEqualTo(0);
        assertThat(recent.get("data").get(0).get("dishId").asLong()).isEqualTo(DISH_FANQIE_CHAODAN);

        // 库存回写：番茄用完 → NONE（保质期清除，不再临期）；鸡蛋用了一些 → LOW
        JsonNode tomato = get(token, "/pantry/item?ingredientId=" + ING_TOMATO).get("data");
        assertThat(tomato.get("level").asText()).isEqualTo("NONE");
        assertThat(tomato.get("bestBefore").isNull()).isTrue();
        assertThat(tomato.get("changes").get(0).get("action").asText()).isEqualTo("cook");
        assertThat(get(token, "/pantry/item?ingredientId=2").get("data").get("level").asText()).isEqualTo("LOW");
        boolean stillExpiring = false;
        for (JsonNode e : get(token, "/pantry/expiring?days=3").get("data")) {
            stillExpiring |= e.get("ingredientId").asLong() == ING_TOMATO;
        }
        assertThat(stillExpiring).as("用完的番茄不应再出现在临期").isFalse();

        // 食记可见（按菜汇总含番茄炒蛋）
        String ym = LocalDate.now().toString().substring(0, 7);
        JsonNode byDish = get(token, "/food-log/by-dish?month=" + ym);
        assertThat(byDish.get("code").asInt()).isEqualTo(0);
        boolean inLog = false;
        for (JsonNode it : byDish.get("data").get("items")) {
            inLog |= it.get("dishId").asLong() == DISH_FANQIE_CHAODAN;
        }
        assertThat(inLog).as("食记应可见单菜直做").isTrue();
    }

    // ---------------- HTTP 工具 ----------------

    /** POST（带 Authorization header）。 */
    // ---------------- multi-tenant-family G.2：家庭主链路 + 账号合并 ----------------

    /** 微信 code2session 打桩（E2E 不连微信）：code → openid 原样返回。 */
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.gudu.xsd.modules.auth.WxClient wxClient;

    private static final String E2E_PW = "e2ePass123";

    /** 超管代建手机号账号（自动建「我的家」）后登录，返回登录响应 data。 */
    private JsonNode registerAndLogin(String phone, String nickname) {
        String admin = loginAdmin();
        JsonNode created = post(admin, "/account", Map.of("phone", phone, "password", E2E_PW, "nickname", nickname));
        assertThat(created.get("code").asInt()).as("代建账号 msg=" + text(created, "msg")).isEqualTo(0);
        JsonNode login = post("/auth/login", Map.of("username", phone, "password", E2E_PW));
        assertThat(login.get("code").asInt()).as("登录 msg=" + text(login, "msg")).isEqualTo(0);
        return login.get("data");
    }

    private JsonNode wxLogin(String openid, String inviteCode) {
        org.mockito.Mockito.when(wxClient.code2session("code-" + openid)).thenReturn(openid);
        Map<String, String> body = new HashMap<>();
        body.put("code", "code-" + openid);
        if (inviteCode != null) body.put("inviteCode", inviteCode);
        JsonNode r = post("/auth/wx-login", body);
        assertThat(r.get("code").asInt()).as("微信登录 msg=" + text(r, "msg")).isEqualTo(0);
        return r.get("data");
    }

    @Test
    void 家庭主链路_注册建家_邀请_加入_切换_退出() {
        // 注册：新账号自动建「我的家」并成为 OWNER
        JsonNode owner = registerAndLogin("13900000201", "E2E家长");
        String ownerToken = owner.get("token").asText();
        long familyA = owner.get("currentFamilyId").asLong();
        assertThat(owner.get("families").get(0).get("role").asText()).isEqualTo("OWNER");
        assertThat(put(ownerToken, "/family/" + familyA, Map.of("name", "E2E幸福一家")).get("code").asInt()).isZero();

        // 邀请：生成 7 天邀请码，游客可预览
        JsonNode inv = post(ownerToken, "/family/" + familyA + "/invitations", null);
        assertThat(inv.get("code").asInt()).isZero();
        String code = inv.get("data").get("code").asText();
        JsonNode preview = get(null, "/family/invitations/" + code);
        assertThat(preview.get("data").get("familyName").asText()).isEqualTo("E2E幸福一家");
        assertThat(preview.get("data").get("usable").asBoolean()).isTrue();

        // 加入：另一个账号凭码加入，当前家庭切到被邀请的家庭
        JsonNode kid = registerAndLogin("13900000202", "E2E孩子");
        String kidToken = kid.get("token").asText();
        long kidHome = kid.get("currentFamilyId").asLong();
        JsonNode joined = post(kidToken, "/family/join", Map.of("code", code));
        assertThat(joined.get("code").asInt()).as("加入 msg=" + text(joined, "msg")).isZero();
        assertThat(joined.get("data").get("familyId").asLong()).isEqualTo(familyA);
        JsonNode cur = get(kidToken, "/family/current");
        assertThat(cur.get("data").get("myRole").asText()).isEqualTo("MEMBER");
        assertThat(cur.get("data").get("members")).hasSize(2);
        // 重复加入拒绝
        assertThat(post(kidToken, "/family/join", Map.of("code", code)).get("code").asInt()).isNotZero();

        // 切换：回自己的家 → 再切回来；切到未加入的家庭 404
        assertThat(post(kidToken, "/family/current", Map.of("familyId", kidHome)).get("code").asInt()).isZero();
        assertThat(get(kidToken, "/family/mine").get("data").get("families")).hasSize(2);
        assertThat(post(kidToken, "/family/current", Map.of("familyId", familyA)).get("code").asInt()).isZero();
        assertThat(post(kidToken, "/family/current", Map.of("familyId", 999999999L)).get("code").asInt()).isEqualTo(404);

        // 主理人不能退出；转让后原主理人为 ADMIN
        assertThat(post(ownerToken, "/family/" + familyA + "/leave", null).get("code").asInt()).isNotZero();

        // 退出：成员退出后家庭只剩主理人，退出者回到自己的家
        assertThat(post(kidToken, "/family/" + familyA + "/leave", null).get("code").asInt()).isZero();
        assertThat(get(ownerToken, "/family/current").get("data").get("members")).hasSize(1);
        assertThat(get(kidToken, "/family/current").get("data").get("id").asLong()).isEqualTo(kidHome);
    }

    @Test
    void 账号合并_微信建号写数据_并入手机号账号_微信重新登录进入手机号账号且数据可见() {
        // 手机号账号 P（自己的家 F2）
        JsonNode p = registerAndLogin("13900000203", "E2E老张");
        long pId = p.get("accountId").asLong();
        long f2 = p.get("currentFamilyId").asLong();

        // 微信账号 W：首次登录自动建号建家 F1，并写一条数据（食集）
        JsonNode w = wxLogin("E2E_oid_merge_1", null);
        String wToken = w.get("token").asText();
        long f1 = w.get("currentFamilyId").asLong();
        assertThat(f1).isNotEqualTo(f2);
        JsonNode menu = post(wToken, "/menu", Map.of("menu", Map.of("name", "E2E微信时期的食集", "servingCount", 1),
                "dishes", List.of()));
        assertThat(menu.get("code").asInt()).isZero();
        long menuId = menu.get("data").asLong();

        // 密码错误：拒绝，两个账号都不变
        assertThat(post(wToken, "/account/merge-phone", Map.of("phone", "13900000203", "password", "wrong-pw"))
                .get("code").asInt()).isNotZero();

        // 合并：返回 P 的新 token，当前家庭沿用 F1
        JsonNode merged = post(wToken, "/account/merge-phone", Map.of("phone", "13900000203", "password", E2E_PW));
        assertThat(merged.get("code").asInt()).as("合并 msg=" + text(merged, "msg")).isZero();
        assertThat(merged.get("data").get("accountId").asLong()).isEqualTo(pId);
        assertThat(merged.get("data").get("currentFamilyId").asLong()).isEqualTo(f1);
        assertThat(merged.get("data").get("families")).hasSize(2);

        // W 的旧 token 失效
        assertThat(get(wToken, "/account/me").get("code").asInt()).isEqualTo(401);

        // 微信重新登录 → 进入 P，F1 的数据可见
        JsonNode again = wxLogin("E2E_oid_merge_1", null);
        assertThat(again.get("accountId").asLong()).isEqualTo(pId);
        String newToken = again.get("token").asText();
        assertThat(post(newToken, "/family/current", Map.of("familyId", f1)).get("code").asInt()).isZero();
        assertThat(get(newToken, "/menu/" + menuId).get("data").get("menu").get("name").asText())
                .isEqualTo("E2E微信时期的食集");
        // 手机号登录同样是 P，两个家庭都在
        JsonNode byPhone = post("/auth/login", Map.of("username", "13900000203", "password", E2E_PW));
        assertThat(byPhone.get("data").get("families")).hasSize(2);
    }

    @Test
    void 账号合并_手机号账号已绑定其他微信_冲突_两个账号都不变() {
        registerAndLogin("13900000204", "E2E已绑定");
        // 先让一个微信账号并入，使手机号账号绑定 openid-A
        String w1 = wxLogin("E2E_oid_conflict_a", null).get("token").asText();
        assertThat(post(w1, "/account/merge-phone", Map.of("phone", "13900000204", "password", E2E_PW))
                .get("code").asInt()).isZero();
        // 另一个微信账号再并入同一手机号账号 → ACCOUNT_WECHAT_CONFLICT
        JsonNode w2 = wxLogin("E2E_oid_conflict_b", null);
        JsonNode r = post(w2.get("token").asText(), "/account/merge-phone",
                Map.of("phone", "13900000204", "password", E2E_PW));
        assertThat(r.get("code").asInt()).isEqualTo(409);
        assertThat(text(r, "msg")).contains("ACCOUNT_WECHAT_CONFLICT");
        assertThat(get(w2.get("token").asText(), "/account/me").get("code").asInt()).as("W2 未失效").isZero();
    }

    @Test
    void 注销_有其他成员的主理人拒绝_普通成员注销后匿名化且token失效() {
        JsonNode owner = registerAndLogin("13900000205", "E2E注销主理人");
        String ownerToken = owner.get("token").asText();
        long family = owner.get("currentFamilyId").asLong();
        String code = post(ownerToken, "/family/" + family + "/invitations", null).get("data").get("code").asText();
        JsonNode member = registerAndLogin("13900000206", "E2E注销成员");
        String memberToken = member.get("token").asText();
        assertThat(post(memberToken, "/family/join", Map.of("code", code)).get("code").asInt()).isZero();

        JsonNode denied = delete(ownerToken, "/account/me");
        assertThat(denied.get("code").asInt()).isNotZero();
        assertThat(text(denied, "msg")).contains("转让");

        assertThat(delete(memberToken, "/account/me").get("code").asInt()).isZero();
        assertThat(get(memberToken, "/account/me").get("code").asInt()).isEqualTo(401);
        assertThat(post("/auth/login", Map.of("username", "13900000206", "password", E2E_PW)).get("code").asInt())
                .isNotZero();
        JsonNode cur = get(ownerToken, "/family/current").get("data");
        assertThat(cur.get("members")).hasSize(1);
    }

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

    /** V55 P2-6：食材详情已删字段不再返回（反向断言，防字段回滚）。 */
    @Test
    void V55_食材详情_已删单位字段不返回() {
        String token = loginAdmin();
        JsonNode r = get(token, "/ingredient/" + ING_TOMATO);
        assertThat(r.get("code").asInt())
                .as("食材详情应成功 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode d = r.get("data");
        assertThat(d.has("unitName")).as("ingredient.unitName 应已删（V55）").isFalse();
        assertThat(d.has("unitGramCount")).as("unitGramCount 应已删").isFalse();
        assertThat(d.has("defaultGramSet")).as("defaultGramSet 应已删").isFalse();
        assertThat(d.has("stockUnitName")).as("stockUnitName 应已删").isFalse();
        assertThat(d.has("price")).as("ingredient.price 应已删").isFalse();
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
