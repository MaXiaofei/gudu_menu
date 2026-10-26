package com.gudu.xsd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E 接口覆盖补全（2026-08-14）：GuduE2EFlowTest 保核心业务链路（14 场景），
 * 本文件补齐长尾接口的端到端覆盖 —— 食材/菜谱/草稿 CRUD、备菜与食集菜品管理、
 * 采购完整链路（自定义项/勾选/入库/撤回）、库存档位、评价、收藏、食记、
 * 通知、AI 估算与日志、member/auth、字典与指标、备份导入、一起吃、排菜扩展、文件上传。
 *
 * <p>写法约定：每组一个 @Test 串多个接口（真实业务顺序），断言 code=0 + 关键字段；
 * 自建资源自清理（建了就删），不破坏种子（admin/番茄/番茄炒蛋/member1/2）。
 *
 * <p>种子同 GuduE2EFlowTest：每 @Test 前 e2e-seed.sql 重置动态表。
 * 不测（另行说明）：POST /dish/import-url（依赖下厨房外网，有 RecipeImporterTest 单测）、
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:e2e-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:e2e-teardown.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class GuduE2ECoverageTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private com.gudu.xsd.config.WxProperties wxProps;

    private final ObjectMapper om = new ObjectMapper();

    private static final long DISH_FANQIE = 1L;   // 番茄炒蛋（种子）
    private static final long ING_TOMATO = 1L;    // 番茄（种子）
    private static final long UNIT_G = 20L;       // g（种子字典）
    private static final long MEMBER_CHEF = 1L;   // 张爸爸 掌勺
    private static final String CTX = "";

    // ===================== HTTP helper（同 GuduE2EFlowTest 范式） =====================

    private String loginAdmin() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "admin123");
        JsonNode r = post(null, "/auth/login", body);
        assertThat(r.get("code").asInt()).as("登录应成功").isEqualTo(0);
        String token = r.get("data").get("token").asText();
        assertThat(token).isNotBlank();
        return token;
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
        if (body != null && !(body instanceof MultiValueMap)) h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("Authorization", token);
        try {
            HttpEntity<?> entity = new HttpEntity<>(body, h);
            ResponseEntity<String> resp = http.exchange(CTX + path, method, entity, String.class);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException(method + " " + path + " 失败: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    // ===================== A. 食材完整 CRUD（V55 后无 unitId） =====================

    @Test
    void A_食材CRUD_新建更新删除_无单位字段() {
        String token = loginAdmin();

        // 新建（V55：body 只有 name/purchaseCategoryId/edible + 营养 EAV）
        Map<String, Object> ing = new HashMap<>();
        ing.put("name", "E2E菜心");
        ing.put("purchaseCategoryId", 24L);
        ing.put("edible", 1);
        Map<String, Object> nut = Map.of("metricId", 1L, "value", 20);
        JsonNode created = post(token, "/ingredient", Map.of("ingredient", ing, "nutritions", List.of(nut)));
        assertThat(created.get("code").asInt()).as("新建食材 msg=" + text(created, "msg")).isEqualTo(0);
        long id = created.get("data").asLong();
        assertThat(id).isPositive();

        // 详情：营养挂上；V55 已删字段不返回
        JsonNode detail = get(token, "/ingredient/" + id);
        assertThat(detail.get("code").asInt()).isEqualTo(0);
        JsonNode d = detail.get("data");
        assertThat(d.get("name").asText()).isEqualTo("E2E菜心");
        assertThat(d.get("nutrition").get("calorie").asDouble()).isEqualTo(20.0);
        assertThat(d.has("unitName")).as("V55 已删 unitName").isFalse();

        // 更新食用属性
        Map<String, Object> upd = new HashMap<>(ing);
        upd.put("id", id);
        upd.put("edible", 3);
        assertThat(put(token, "/ingredient", upd).get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/ingredient/" + id).get("data").get("edible").asInt()).isEqualTo(3);

        // 列表搜索命中
        JsonNode page = get(token, "/ingredient?keyword=E2E菜心&pageNum=1&pageSize=10");
        assertThat(page.get("data").get("total").asInt()).isEqualTo(1);

        // 删除（逻辑删）→ 详情 data 为 null
        assertThat(delete(token, "/ingredient/" + id).get("code").asInt()).isEqualTo(0);
        JsonNode after = get(token, "/ingredient/" + id);
        assertThat(after.get("data").isNull()).isTrue();
    }

    // ===================== B. 菜谱 CRUD（含 V55 用量原文回填） =====================

    @Test
    void B_菜谱CRUD_保存详情搜索更新删除() {
        String token = loginAdmin();

        // 保存（掌勺权限）：番茄 300g（unitId 保留，V55 不再换算克）
        Map<String, Object> dishBody = Map.of(
                "dish", Map.of("name", "E2E覆盖菜", "difficulty", 2, "cookTime", 10),
                "ingredients", List.of(Map.of("ingredientId", ING_TOMATO, "amount", 300, "unitId", UNIT_G)),
                "steps", List.of(Map.of("seq", 1, "text", "热锅下油")),
                "cuisineIds", List.of(), "tagIds", List.of(), "categoryIds", List.of());
        JsonNode created = post(token, "/dish", dishBody);
        assertThat(created.get("code").asInt()).as("保存菜谱 msg=" + text(created, "msg")).isEqualTo(0);
        long dishId = created.get("data").asLong();

        // 详情：用量原文回填 unitName=g
        JsonNode detail = get(token, "/dish/" + dishId);
        assertThat(detail.get("code").asInt()).isEqualTo(0);
        JsonNode ing = detail.get("data").get("ingredients").get(0);
        assertThat(ing.get("unitName").asText()).isEqualTo("g");
        assertThat(ing.get("amount").asDouble()).isEqualTo(300.0);
        assertThat(ing.has("grams")).as("V55 已停用 grams").isFalse();

        // POST 搜索（keyword）
        JsonNode search = post(token, "/dish/search", Map.of("keyword", "E2E覆盖", "pageNum", 1, "pageSize", 10));
        assertThat(search.get("code").asInt()).isEqualTo(0);
        assertThat(search.get("data").get("total").asInt()).isEqualTo(1);

        // GET 搜索（sort=cooked）
        JsonNode searchGet = get(token, "/dish/search?sort=cooked&pageNum=1&pageSize=5");
        assertThat(searchGet.get("code").asInt()).isEqualTo(0);
        assertThat(searchGet.get("data").get("total").asInt()).isPositive();

        // 更新（改名）→ 详情确认（内层 Map.of 不可变，必须重建可变 Map）
        @SuppressWarnings("unchecked")
        Map<String, Object> updDish = new HashMap<>((Map<String, Object>) dishBody.get("dish"));
        updDish.put("id", dishId);
        updDish.put("name", "E2E覆盖菜改");
        Map<String, Object> updBody = new HashMap<>(dishBody);
        updBody.put("dish", updDish);
        JsonNode updResp = put(token, "/dish", updBody);
        assertThat(updResp.get("code").asInt()).as("更新菜谱 msg=" + text(updResp, "msg")).isEqualTo(0);
        assertThat(get(token, "/dish/" + dishId).get("data").get("dish").get("name").asText())
                .isEqualTo("E2E覆盖菜改");

        // 菜谱营养（番茄有 per100g=19）
        JsonNode nutr = get(token, "/dish/" + dishId + "/nutrition?serving=1");
        assertThat(nutr.get("code").asInt()).isEqualTo(0);
        assertThat(nutr.get("data").has("1")).isTrue();

        // 删除
        assertThat(delete(token, "/dish/" + dishId).get("code").asInt()).isEqualTo(0);
    }

    // ===================== C. 菜谱草稿 =====================

    @Test
    void C_菜谱草稿_建列表查改删() {
        String token = loginAdmin();

        Map<String, Object> draft = Map.of(
                "name", "E2E草稿菜",
                "ingredients", List.of(Map.of("ingredientId", ING_TOMATO, "amount", "2", "unitText", "个")),
                "steps", List.of(Map.of("seq", 1, "text", "草稿步骤")));
        JsonNode created = post(token, "/dish/draft", draft);
        assertThat(created.get("code").asInt()).as("存草稿 msg=" + text(created, "msg")).isEqualTo(0);
        long draftId = created.get("data").asLong();

        // 列表
        JsonNode list = get(token, "/dish/draft/list?pageNum=1&pageSize=10");
        assertThat(list.get("code").asInt()).isEqualTo(0);
        assertThat(list.get("data").get("total").asInt()).isEqualTo(1);

        // 详情（自由文本 unitText 保留）
        JsonNode detail = get(token, "/dish/draft/" + draftId);
        assertThat(detail.get("code").asInt()).isEqualTo(0);
        assertThat(detail.get("data").get("name").asText()).isEqualTo("E2E草稿菜");

        // 更新
        Map<String, Object> upd = new HashMap<>(draft);
        upd.put("id", draftId);
        upd.put("name", "E2E草稿菜2");
        assertThat(post(token, "/dish/draft", upd).get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/dish/draft/" + draftId).get("data").get("name").asText())
                .isEqualTo("E2E草稿菜2");

        // 删除 → 列表空
        assertThat(delete(token, "/dish/draft/" + draftId).get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/dish/draft/list?pageNum=1&pageSize=10").get("data").get("total").asInt())
                .isEqualTo(0);
    }

    // ===================== D. 食集备菜 + 菜品管理 + 再做一次 =====================

    @Test
    void D_食集备菜状态_菜品备注移除_复制() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 自建食集挂番茄炒蛋
        JsonNode menuCreated = post(token, "/menu", Map.of(
                "menu", Map.of("name", "E2E备菜食集", "servingCount", 1),
                "dishes", List.of(Map.of("dishId", DISH_FANQIE, "servingFactor", 1))));
        long menuId = menuCreated.get("data").asLong();

        // 备菜清单（V55：usageTexts 用量原文）
        JsonNode prep = get(token, "/menu/" + menuId + "/prep");
        assertThat(prep.get("code").asInt()).isEqualTo(0);
        assertThat(prep.get("data").get("totalCount").asInt()).isEqualTo(2); // 番茄+鸡蛋
        JsonNode firstItem = prep.get("data").get("items").get(0);
        assertThat(firstItem.get("usageTexts").size()).isPositive();
        assertThat(firstItem.get("status").asText()).isEqualTo("PENDING");
        long ingId = firstItem.get("ingredientId").asLong();

        // 备菜状态 → READY
        assertThat(put(token, "/menu/" + menuId + "/prep/" + ingId + "?status=READY", null)
                .get("code").asInt()).isEqualTo(0);
        JsonNode prep2 = get(token, "/menu/" + menuId + "/prep");
        assertThat(prep2.get("data").get("readyCount").asInt()).isEqualTo(1);

        // 菜备注
        assertThat(put(token, "/menu/" + menuId + "/dish/" + DISH_FANQIE + "/note", Map.of("note", "少辣"))
                .get("code").asInt()).isEqualTo(0);
        JsonNode detail = get(token, "/menu/" + menuId);
        assertThat(detail.get("data").get("dishes").get(0).get("note").asText()).isEqualTo("少辣");

        // 再做一次（复制）
        JsonNode copied = post(token, "/menu/" + menuId + "/copy", null);
        assertThat(copied.get("code").asInt()).isEqualTo(0);
        long copyId = copied.get("data").asLong();
        JsonNode copyDetail = get(token, "/menu/" + copyId);
        assertThat(copyDetail.get("data").get("dishes").size()).isEqualTo(1);
        assertThat(copyDetail.get("data").get("menu").get("status").asText()).isEqualTo("ACTIVE");

        // 移除菜 → dishes 空
        assertThat(delete(token, "/menu/" + menuId + "/dish/" + DISH_FANQIE).get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/menu/" + menuId).get("data").get("dishes").size()).isEqualTo(0);

        // 清理
        delete(token, "/menu/" + menuId);
        delete(token, "/menu/" + copyId);
    }

    // ===================== E1. 采购：空清单 + 自定义项 + 改名 + 删 =====================

    @Test
    void E1_采购空清单_自定义项命中与未命中_改名删项删单() {
        String token = loginAdmin();

        JsonNode created = post(token, "/shopping/create", null);
        assertThat(created.get("code").asInt()).as("建空清单 msg=" + text(created, "msg")).isEqualTo(0);
        long listId = created.get("data").asLong();

        // 自定义项：命中食材库（番茄）→ 关联
        JsonNode hit = post(token, "/shopping/item/custom",
                Map.of("listId", listId, "name", "番茄", "amount", 2));
        assertThat(hit.get("code").asInt()).isEqualTo(0);
        long hitId = hit.get("data").asLong();

        // 自定义项：未命中 → customName 存名
        JsonNode miss = post(token, "/shopping/item/custom",
                Map.of("listId", listId, "name", "E2E自定义物品"));
        assertThat(miss.get("code").asInt()).isEqualTo(0);
        long missId = miss.get("data").asLong();

        // 清单详情：两项，未命中项 displayName=自定义名
        JsonNode detail = get(token, "/shopping/" + listId);
        assertThat(detail.get("code").asInt()).isEqualTo(0);
        assertThat(detail.get("data").get("items").size()).isEqualTo(2);
        boolean hasCustom = false;
        for (JsonNode it : detail.get("data").get("items")) {
            if ("E2E自定义物品".equals(it.get("ingredientName").asText())) hasCustom = true;
        }
        assertThat(hasCustom).as("未命中项应以 customName 展示").isTrue();

        // by-menu（无关联清单 → data null）
        assertThat(get(token, "/shopping/by-menu/999").get("data").isNull()).isTrue();

        // 清单分页
        JsonNode page = get(token, "/shopping?pageNum=1&pageSize=5");
        assertThat(page.get("code").asInt()).isEqualTo(0);

        // 改名
        assertThat(put(token, "/shopping/" + listId + "/name", Map.of("name", "E2E采购单"))
                .get("code").asInt()).isEqualTo(0);

        // 删项 → 1 项
        assertThat(delete(token, "/shopping/item/" + missId).get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/shopping/" + listId).get("data").get("items").size()).isEqualTo(1);

        // 删清单
        assertThat(delete(token, "/shopping/" + listId).get("code").asInt()).isEqualTo(0);
    }

    // ===================== E2. 采购：勾选已买 + 批量入库 + 撤回 =====================

    @Test
    void E2_采购生成_勾选已买_批量入库_撤回() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 从番茄炒蛋生成（番茄+鸡蛋两项）
        JsonNode gen = post(token, "/shopping/generate",
                Map.of("sourceType", "dish", "sourceIds", List.of(DISH_FANQIE)));
        assertThat(gen.get("code").asInt()).isEqualTo(0);
        long listId = gen.get("data").asLong();
        JsonNode detail = get(token, "/shopping/" + listId);
        assertThat(detail.get("data").get("items").size()).isEqualTo(2);

        long tomatoItemId = -1, eggItemId = -1;
        for (JsonNode it : detail.get("data").get("items")) {
            long ingId = it.get("ingredientId").asLong();
            if (ingId == ING_TOMATO) tomatoItemId = it.get("id").asLong();
            else eggItemId = it.get("id").asLong();
        }
        assertThat(tomatoItemId).isPositive();
        assertThat(eggItemId).isPositive();

        // 勾选已买（0→1 入库设档位）
        assertThat(put(token, "/shopping/item/" + tomatoItemId + "/purchased", Map.of("level", "ENOUGH"))
                .get("code").asInt()).isEqualTo(0);
        for (JsonNode it : get(token, "/shopping/" + listId).get("data").get("items")) {
            if (it.get("id").asLong() == tomatoItemId) {
                assertThat(it.get("purchased").asInt()).isEqualTo(1);
            }
        }

        // 批量入库（鸡蛋）
        JsonNode restock = post(token, "/shopping/restock", Map.of("itemIds", List.of(eggItemId)));
        assertThat(restock.get("code").asInt()).as("入库 msg=" + text(restock, "msg")).isEqualTo(0);
        assertThat(restock.get("data").get("restocked").asInt()).isEqualTo(1);

        // from-prep（食集聚合加采购，重复食材去重）
        JsonNode fromPrep = post(token, "/shopping/from-prep",
                Map.of("menuId", 999L, "ingredientIds", List.of(ING_TOMATO)));
        assertThat(fromPrep.get("code").asInt()).isEqualTo(0);

        // 撤回入库（按流水恢复 + 删该项）
        assertThat(post(token, "/shopping/item/" + eggItemId + "/undo-restock", null)
                .get("code").asInt()).isEqualTo(0);
        JsonNode after = get(token, "/shopping/" + listId);
        for (JsonNode it : after.get("data").get("items")) {
            assertThat(it.get("id").asLong()).as("撤回应删除该项").isNotEqualTo(eggItemId);
        }

        delete(token, "/shopping/" + listId);
    }

    // ===================== F. 库存档位完整链路 =====================

    @Test
    void F_库存档位_手动入库改档查询删档_临期() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 手动入库（接口返回 void；直接对种子番茄建档，V55 无数量单位）
        JsonNode manual = post(token, "/pantry/manual",
                Map.of("ingredientId", ING_TOMATO, "level", "ENOUGH", "sourceNote", "e2e入库"));
        assertThat(manual.get("code").asInt()).as("手动入库 msg=" + text(manual, "msg")).isEqualTo(0);
        long ingId = ING_TOMATO;
        assertThat(ingId).isPositive();

        // 改档位 → LOW
        assertThat(put(token, "/pantry/" + ingId + "/level", Map.of("level", "LOW", "note", "e2e改档"))
                .get("code").asInt()).isEqualTo(0);

        // 分组列表（结构：summary 计数 + 单列表 items）：不足计数 + 番茄在列表中档位 LOW
        JsonNode grouped = get(token, "/pantry/grouped");
        assertThat(grouped.get("code").asInt()).isEqualTo(0);
        assertThat(grouped.get("data").get("summary").get("low").asInt())
                .as("改档后「不足」计数应 ≥1").isPositive();
        boolean inLow = false;
        for (JsonNode it : grouped.get("data").get("items")) {
            if (it.get("ingredientId").asLong() == ingId && "LOW".equals(it.get("level").asText())) {
                inLow = true;
            }
        }
        assertThat(inLow).as("番茄应出现在列表且档位为 LOW").isTrue();

        // 食材明细：档位 + 流水
        JsonNode item = get(token, "/pantry/item?ingredientId=" + ingId);
        assertThat(item.get("code").asInt()).isEqualTo(0);
        assertThat(item.get("data").get("level").asText()).isEqualTo("LOW");
        assertThat(item.get("data").get("changes").size()).isPositive();

        // 删档 → 回到未建档
        assertThat(delete(token, "/pantry/" + ingId + "/level").get("code").asInt()).isEqualTo(0);

        // 临期（种子有明天过期的番茄批次）
        JsonNode expiring = get(token, "/pantry/expiring?days=3");
        assertThat(expiring.get("code").asInt()).isEqualTo(0);
        assertThat(expiring.get("data").isArray()).isTrue();
    }

    // ===================== G. 评价 =====================

    @Test
    void G_评价_写菜评价均分我的_食集总览() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 写菜评价
        JsonNode created = post(token, "/review",
                Map.of("dishId", DISH_FANQIE, "starRating", 5, "text", "E2E好吃"));
        assertThat(created.get("code").asInt()).as("写评价 msg=" + text(created, "msg")).isEqualTo(0);

        // 菜评价列表
        JsonNode list = get(token, "/review/dish/" + DISH_FANQIE);
        assertThat(list.get("code").asInt()).isEqualTo(0);
        assertThat(list.get("data").size()).isPositive();
        assertThat(list.get("data").get(0).get("text").asText()).isEqualTo("E2E好吃");

        // 均分
        JsonNode avg = get(token, "/review/dish/" + DISH_FANQIE + "/avg");
        assertThat(avg.get("code").asInt()).isEqualTo(0);
        assertThat(avg.get("data").get("count").asInt()).isEqualTo(1);

        // 我的评价
        JsonNode mine = get(token, "/review/mine");
        assertThat(mine.get("code").asInt()).isEqualTo(0);

        // 自建食集 → 食集总览
        long menuId = post(token, "/menu", Map.of(
                "menu", Map.of("name", "E2E评价食集"),
                "dishes", List.of(Map.of("dishId", DISH_FANQIE, "servingFactor", 1))))
                .get("data").asLong();
        JsonNode overview = get(token, "/review/menu-overview/" + menuId);
        assertThat(overview.get("code").asInt()).isEqualTo(0);

        // 食集评价列表（空）
        assertThat(get(token, "/review/menu/" + menuId).get("code").asInt()).isEqualTo(0);

        delete(token, "/menu/" + menuId);
    }

    // ===================== H. 收藏/按食材找菜 =====================

    @Test
    void H_收藏_加收藏列表按食材找菜取消() {
        String token = loginAdmin();

        // 加收藏
        assertThat(post(token, "/cookbook/favorite/" + DISH_FANQIE + "?memberId=" + MEMBER_CHEF, null)
                .get("code").asInt()).isEqualTo(0);

        // 收藏列表含番茄炒蛋
        JsonNode favs = get(token, "/cookbook/favorites?memberId=" + MEMBER_CHEF);
        assertThat(favs.get("code").asInt()).isEqualTo(0);
        boolean has = false;
        for (JsonNode d : favs.get("data")) {
            if (d.get("id").asLong() == DISH_FANQIE) has = true;
        }
        assertThat(has).as("收藏列表应含番茄炒蛋").isTrue();

        // 按食材找菜（含番茄的菜）
        JsonNode match = get(token, "/cookbook/by-ingredients?ingredientIds=" + ING_TOMATO);
        assertThat(match.get("code").asInt()).isEqualTo(0);
        assertThat(match.get("data").size()).isPositive();

        // 取消收藏 → 列表空
        assertThat(delete(token, "/cookbook/favorite/" + DISH_FANQIE + "?memberId=" + MEMBER_CHEF)
                .get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/cookbook/favorites?memberId=" + MEMBER_CHEF).get("data").size()).isEqualTo(0);
    }

    // ===================== I. 食记 food-log + 做菜历史 =====================

    @Test
    void I_食记_做菜写记录_月年按菜明细_做菜历史() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 自建食集挂菜并做菜（写 cooking_record）
        long menuId = post(token, "/menu", Map.of(
                "menu", Map.of("name", "E2E食记食集"),
                "dishes", List.of(Map.of("dishId", DISH_FANQIE, "servingFactor", 1))))
                .get("data").asLong();
        JsonNode cook = post(token, "/menu/" + menuId + "/cook",
                Map.of("usedUp", List.of(ING_TOMATO), "partiallyUsed", List.of()));
        assertThat(cook.get("code").asInt()).isEqualTo(0);

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));

        // 月食记
        JsonNode monthLog = get(token, "/food-log/month?month=" + month);
        assertThat(monthLog.get("code").asInt()).isEqualTo(0);

        // 按菜统计
        JsonNode byDish = get(token, "/food-log/by-dish?month=" + month);
        assertThat(byDish.get("code").asInt()).isEqualTo(0);

        // 年食记
        JsonNode yearLog = get(token, "/food-log/year?year=" + year);
        assertThat(yearLog.get("code").asInt()).isEqualTo(0);

        // 食记明细（按食集）
        JsonNode detail = get(token, "/food-log/detail?menuId=" + menuId);
        assertThat(detail.get("code").asInt()).isEqualTo(0);

        // 做菜历史
        JsonNode history = get(token, "/dish/" + DISH_FANQIE + "/history");
        assertThat(history.get("code").asInt()).isEqualTo(0);

        delete(token, "/menu/" + menuId);
    }

    // ===================== J. 通知 =====================

    @Test
    void J_通知_扫描临期_列表已读未读数() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 手动触发临期扫描（种子有明天过期的番茄批次 → 掌勺成员收通知）
        JsonNode scan = post(token, "/notification/scan-expiring?days=3", null);
        assertThat(scan.get("code").asInt()).isEqualTo(0);

        // 通知列表
        JsonNode list = get(token, "/notification");
        assertThat(list.get("code").asInt()).isEqualTo(0);
        JsonNode items = list.get("data");
        assertThat(items.isArray() && items.size() > 0).as("临期扫描后应有通知").isTrue();

        // 未读数 > 0
        JsonNode unread = get(token, "/notification/unread-count");
        assertThat(unread.get("data").get("count").asInt()).isPositive();

        // 标第一条已读 → 未读减一
        long firstId = items.get(0).get("id").asLong();
        assertThat(put(token, "/notification/" + firstId + "/read", null).get("code").asInt()).isEqualTo(0);
        int afterCount = get(token, "/notification/unread-count").get("data").get("count").asInt();
        int beforeCount = unread.get("data").get("count").asInt();
        assertThat(afterCount).isEqualTo(beforeCount - 1);
    }

    // ===================== K. AI 估算 + 调用日志 + provider =====================

    @Test
    void K_AI估算_调用日志用量_provider查询() {
        String token = loginAdmin();

        // 文字估营养（mock provider 返回 5 项指标）
        JsonNode est = post(token, "/ai/dish/estimate",
                Map.of("description", "一盘番茄炒蛋约两人份", "servingFactor", 1));
        assertThat(est.get("code").asInt()).as("AI 估算 msg=" + text(est, "msg")).isEqualTo(0);
        assertThat(est.get("data").get("nutrition").size()).isPositive();

        // 调用日志（含本次 dish_estimate）
        JsonNode callLog = get(token, "/ai/call-log?scene=dish_estimate&pageNum=1&pageSize=5");
        assertThat(callLog.get("code").asInt()).isEqualTo(0);
        assertThat(callLog.get("data").get("total").asLong()).isPositive();

        // 用量统计
        JsonNode usage = get(token, "/ai/usage?days=7");
        assertThat(usage.get("code").asInt()).isEqualTo(0);

        // provider 查询 + 旧切换接口原值回写。旧接口现在改写的是网关库里的 CHAT/TOOLS 路由（staging 共用），
        // 因此先快照两条路由，切换后按快照原样恢复，跑完 staging 路由不变
        JsonNode provider = get(token, "/ai/provider");
        assertThat(provider.get("code").asInt()).isEqualTo(0);
        String current = provider.get("data").get("current").asText();
        JsonNode routesBefore = get(token, "/admin/ai/gateway").get("data").get("routes");
        try {
            assertThat(put(token, "/ai/provider", Map.of("provider", current)).get("code").asInt()).isEqualTo(0);
        } finally {
            restoreRoutes(token, routesBefore, "CHAT", "TOOLS");
        }
    }

    /** 按快照原样写回网关路由（主目标、降级链、超时、重试、思考模式）。 */
    private void restoreRoutes(String token, JsonNode routes, String... capabilities) {
        for (String cap : capabilities) {
            for (JsonNode r : routes) {
                if (!cap.equals(r.get("capability").asText())) continue;
                Map<String, Object> body = new HashMap<>();
                body.put("primary", om.convertValue(r.get("primary"), Map.class));
                body.put("fallbacks", om.convertValue(r.get("fallbacks"), java.util.List.class));
                body.put("timeoutMs", r.get("timeoutMs").asInt());
                body.put("maxRetries", r.get("maxRetries").asInt());
                body.put("thinking", r.get("thinking").asText());
                JsonNode res = put(token, "/admin/ai/gateway/routes/" + cap, body);
                assertThat(res.get("code").asInt()).as("恢复路由 " + cap + " " + res).isZero();
            }
        }
    }

    // ===================== L. member/auth 剩余接口 =====================

    @Test
    void L_成员auth_建成员改查权限营养目标_删除_登出() {
        String token = loginAdmin();

        // 新建成员
        JsonNode created = post(token, "/member", Map.of(
                "name", "E2E成员", "phone", "13900001234", "password", "e2etest123"));
        assertThat(created.get("code").asInt()).as("建成员 msg=" + text(created, "msg")).isEqualTo(0);
        long memberId = created.get("data").asLong();

        // 更新
        Map<String, Object> upd = new HashMap<>();
        upd.put("id", memberId);
        upd.put("name", "E2E成员改");
        assertThat(put(token, "/member", upd).get("code").asInt()).isEqualTo(0);

        // 权限（角色默认+个人并集）
        JsonNode perms = get(token, "/member/" + memberId + "/permissions");
        assertThat(perms.get("code").asInt()).isEqualTo(0);
        assertThat(perms.get("data").isArray()).isTrue();
        // 权限 key 字典
        assertThat(get(token, "/member/permissions/keys").get("code").asInt()).isEqualTo(0);

        // 营养目标（无 healthProfile → data null 或 code=0）
        JsonNode target = get(token, "/member/" + memberId + "/nutrition-target");
        assertThat(target.get("code").asInt()).isEqualTo(0);

        // auth me
        JsonNode me = get(token, "/auth/me");
        assertThat(me.get("code").asInt()).isEqualTo(0);

        // 删除成员
        assertThat(delete(token, "/member/" + memberId).get("code").asInt()).isEqualTo(0);

        // 登出 → 旧 token 失效（me 返回非 0）
        assertThat(post(token, "/auth/logout", null).get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/auth/me").get("code").asInt()).as("登出后旧 token 应失效").isNotEqualTo(0);
    }

    // ===================== N. 字典 + 营养指标 CRUD =====================

    @Test
    void N_字典与指标_自建改删() {
        String token = loginAdmin();

        // 字典：建（sort NOT NULL 必带；V55：unit 字典仍在用，自建测试项用完即删）
        JsonNode dictCreated = post(token, "/dict",
                Map.of("name", "E2E单位", "dictGroup", "unit", "sort", 0));
        assertThat(dictCreated.get("code").asInt()).isEqualTo(0);
        long dictId = dictCreated.get("data").asLong();
        // 改
        Map<String, Object> dictUpd = new HashMap<>();
        dictUpd.put("id", dictId);
        dictUpd.put("name", "E2E单位改");
        dictUpd.put("dictGroup", "unit");
        assertThat(put(token, "/dict", dictUpd).get("code").asInt()).isEqualTo(0);
        // 删
        assertThat(delete(token, "/dict/" + dictId).get("code").asInt()).isEqualTo(0);

        // 指标：建改删（metric_group NOT NULL 必带）
        JsonNode metricCreated = post(token, "/nutrition/metric",
                Map.of("name", "e2e_metric", "unit", "mg", "metricGroup", "micro", "sort", 0));
        assertThat(metricCreated.get("code").asInt()).isEqualTo(0);
        long metricId = metricCreated.get("data").asLong();
        Map<String, Object> mUpd = new HashMap<>();
        mUpd.put("id", metricId);
        mUpd.put("name", "e2e_metric2");
        mUpd.put("unit", "mg");
        assertThat(put(token, "/nutrition/metric", mUpd).get("code").asInt()).isEqualTo(0);
        // 全量列表
        assertThat(get(token, "/nutrition/metric").get("code").asInt()).isEqualTo(0);
        assertThat(delete(token, "/nutrition/metric/" + metricId).get("code").asInt()).isEqualTo(0);
    }

    // ===================== O. 一起吃（协同点菜） =====================

    @Test
    void O_一起吃_建邀请口令token_加入_点菜_改名_搜索_删菜() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + MEMBER_CHEF, null);

        // 自建食集
        long menuId = post(token, "/menu", Map.of(
                "menu", Map.of("name", "E2E一起吃食集"), "dishes", List.of()))
                .get("data").asLong();

        // 生成邀请（口令 + token）
        JsonNode invite = post(token, "/menu/" + menuId + "/invite", null);
        assertThat(invite.get("code").asInt()).as("生成邀请 msg=" + text(invite, "msg")).isEqualTo(0);
        JsonNode iv = invite.get("data");
        String inviteToken = iv.get("token").asText();
        String code = iv.has("code") ? iv.get("code").asText() : null;
        assertThat(inviteToken).isNotBlank();

        // 免登录查邀请（token / 口令两个入口）
        assertThat(get(null, "/invite/" + inviteToken).get("code").asInt()).isEqualTo(0);
        if (code != null && !code.isBlank()) {
            assertThat(get(null, "/invite/code/" + code).get("code").asInt()).isEqualTo(0);
        }

        // 登录成员加入
        JsonNode join = post(token, "/invite/" + inviteToken + "/join", Map.of("nickname", "E2E食客"));
        assertThat(join.get("code").asInt()).isEqualTo(0);

        // 协同页（成员+菜+动态）
        JsonNode together = get(token, "/menu/" + menuId + "/together");
        assertThat(together.get("code").asInt()).isEqualTo(0);

        // 协同点菜（白名单接口，登录成员身份）
        JsonNode addItem = post(token, "/menu/" + menuId + "/together/items",
                Map.of("dishId", DISH_FANQIE));
        assertThat(addItem.get("code").asInt()).as("协同点菜 msg=" + text(addItem, "msg")).isEqualTo(0);

        // 菜搜索（免登录白名单）
        assertThat(get(null, "/menu/" + menuId + "/together/dishes?keyword=番茄").get("code").asInt())
                .isEqualTo(0);

        // 改昵称
        assertThat(put(token, "/menu/" + menuId + "/together/nickname", Map.of("nickname", "E2E食客改"))
                .get("code").asInt()).isEqualTo(0);

        // together-count（占位）
        assertThat(get(token, "/menu/" + menuId + "/together-count").get("code").asInt()).isEqualTo(0);

        // 删协同菜（取加入项的 id）
        JsonNode together2 = get(token, "/menu/" + menuId + "/together");
        long menuDishId = -1;
        if (together2.get("data").has("dishes") && together2.get("data").get("dishes").isArray()) {
            for (JsonNode d : together2.get("data").get("dishes")) {
                if (d.has("id")) { menuDishId = d.get("id").asLong(); break; }
            }
        }
        if (menuDishId > 0) {
            assertThat(delete(token, "/menu/" + menuId + "/together/items/" + menuDishId)
                    .get("code").asInt()).isEqualTo(0);
        }

        delete(token, "/menu/" + menuId);
    }

    // ===================== Q. 文件上传（multipart） =====================

    @Test
    void Q_文件上传_小图返回URL与缩略图() {
        String token = loginAdmin();

        // 1x1 PNG
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() { return "e2e-test.png"; }
        });
        JsonNode up = post(token, "/file/upload", form);
        assertThat(up.get("code").asInt()).as("上传 msg=" + text(up, "msg")).isEqualTo(0);
        JsonNode data = up.get("data");
        assertThat(data.get("url").asText()).contains("/uploads/");
        assertThat(data.has("thumbnailUrl")).isTrue();
    }

    // ===================== P. 余量保质期（feature-gaps-closure G2/G6） =====================

    @Test
    void P_保质期_设档带保质期_临期查询_清除_保质天数建议_入库带保质期() {
        String token = loginAdmin();
        LocalDate in2 = LocalDate.now().plusDays(2);

        // 设档 + 保质期 → 临期（3 天内）能查到，daysLeft=2
        JsonNode set = put(token, "/pantry/" + ING_TOMATO + "/level",
                Map.of("level", "ENOUGH", "bestBefore", in2.toString(), "note", "E2E保质期"));
        assertThat(set.get("code").asInt()).as("设档 msg=" + text(set, "msg")).isEqualTo(0);
        JsonNode exp = get(token, "/pantry/expiring?days=3");
        assertThat(exp.get("code").asInt()).isEqualTo(0);
        JsonNode tomato = null;
        for (JsonNode it : exp.get("data")) {
            if (it.get("ingredientId").asLong() == ING_TOMATO) tomato = it;
        }
        assertThat(tomato).as("临期应含番茄").isNotNull();
        assertThat(tomato.get("daysLeft").asInt()).isEqualTo(2);
        assertThat(tomato.get("bestBefore").asText()).isEqualTo(in2.toString());
        assertThat(tomato.get("level").asText()).isEqualTo("ENOUGH");
        assertThat(tomato.get("ingredientName").asText()).isEqualTo("番茄");

        // grouped / item 带 bestBefore + daysLeft
        JsonNode grouped = get(token, "/pantry/grouped");
        boolean found = false;
        for (JsonNode it : grouped.get("data").get("items")) {
            if (it.get("ingredientId").asLong() == ING_TOMATO) {
                found = true;
                assertThat(it.get("daysLeft").asInt()).isEqualTo(2);
            }
        }
        assertThat(found).isTrue();
        assertThat(get(token, "/pantry/item?ingredientId=" + ING_TOMATO).get("data").get("bestBefore").asText())
                .isEqualTo(in2.toString());

        // 传 null = 清除；不传 = 不改
        Map<String, Object> clear = new HashMap<>();
        clear.put("level", "ENOUGH");
        clear.put("bestBefore", null);
        assertThat(put(token, "/pantry/" + ING_TOMATO + "/level", clear).get("code").asInt()).isEqualTo(0);
        JsonNode item = get(token, "/pantry/item?ingredientId=" + ING_TOMATO).get("data");
        assertThat(item.get("bestBefore").isNull()).as("bestBefore 传 null 应清除").isTrue();
        assertThat(item.get("daysLeft").isNull()).isTrue();

        // 保质天数建议：番茄=蔬菜（V64 默认 5 天）
        JsonNode sl = get(token, "/pantry/shelf-life?ingredientId=" + ING_TOMATO);
        assertThat(sl.get("code").asInt()).isEqualTo(0);
        assertThat(sl.get("data").get("suggestedDays").asInt()).isPositive();

        // 手动入库带 bestBefore（鸡蛋 id=2）
        LocalDate in5 = LocalDate.now().plusDays(5);
        JsonNode manual = post(token, "/pantry/manual",
                Map.of("ingredientId", 2L, "level", "ENOUGH", "bestBefore", in5.toString(), "sourceNote", "E2E入库"));
        assertThat(manual.get("code").asInt()).as("入库 msg=" + text(manual, "msg")).isEqualTo(0);
        assertThat(get(token, "/pantry/item?ingredientId=2").get("data").get("bestBefore").asText())
                .isEqualTo(in5.toString());
    }

    // ===================== Q. 菜谱覆盖率（G3） =====================

    @Test
    void Q_菜谱搜索_登录带覆盖率_sort_coverage家里够的优先() {
        String token = loginAdmin();
        // 种子：番茄/鸡蛋都 ENOUGH → 番茄炒蛋 2/2
        JsonNode r = get(token, "/dish/search?keyword=番茄炒蛋&pageNum=1&pageSize=10");
        assertThat(r.get("code").asInt()).as("search msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode rec = null;
        for (JsonNode d : r.get("data").get("records")) {
            if (d.get("id").asLong() == DISH_FANQIE) rec = d;
        }
        assertThat(rec).as("应搜到番茄炒蛋").isNotNull();
        JsonNode cov = rec.get("coverage");
        assertThat(cov.isNull()).as("登录应带 coverage").isFalse();
        assertThat(cov.get("total").asInt()).isEqualTo(2);
        assertThat(cov.get("enough").asInt()).isEqualTo(2);
        assertThat(cov.get("missing").isArray()).isTrue();

        // 番茄用完 → 1/2，missing 含番茄
        put(token, "/pantry/" + ING_TOMATO + "/level", Map.of("level", "NONE"));
        JsonNode r2 = get(token, "/dish/search?keyword=番茄炒蛋&pageNum=1&pageSize=10");
        JsonNode cov2 = null;
        for (JsonNode d : r2.get("data").get("records")) {
            if (d.get("id").asLong() == DISH_FANQIE) cov2 = d.get("coverage");
        }
        assertThat(cov2.get("enough").asInt()).isEqualTo(1);
        assertThat(cov2.get("missing").get(0).get("name").asText()).isEqualTo("番茄");

        // sort=coverage：按 enough/total 降序
        JsonNode sorted = get(token, "/dish/search?sort=coverage&pageNum=1&pageSize=20");
        assertThat(sorted.get("code").asInt()).isEqualTo(0);
        double prev = 2;
        for (JsonNode d : sorted.get("data").get("records")) {
            JsonNode c = d.get("coverage");
            double ratio = c == null || c.isNull() || c.get("total").asInt() == 0 ? 0
                    : c.get("enough").asDouble() / c.get("total").asDouble();
            assertThat(ratio).as("sort=coverage 应按覆盖率降序").isLessThanOrEqualTo(prev);
            prev = ratio;
        }
    }

    // ===================== R. 常用食材 / 最近做过 / 单菜弹窗（G11/G4/G1） =====================

    @Test
    void R_常用食材_单菜弹窗_最近做过() {
        String token = loginAdmin();
        JsonNode freq = get(token, "/ingredient/frequent?limit=8");
        assertThat(freq.get("code").asInt()).as("frequent msg=" + text(freq, "msg")).isEqualTo(0);
        assertThat(freq.get("data").size()).isBetween(1, 8);
        assertThat(freq.get("data").get(0).has("id")).isTrue();
        assertThat(freq.get("data").get(0).has("name")).isTrue();

        JsonNode mats = get(token, "/cook/dish/" + DISH_FANQIE + "/materials?servings=2");
        assertThat(mats.get("code").asInt()).as("materials msg=" + text(mats, "msg")).isEqualTo(0);
        assertThat(mats.get("data").get("menuId").isNull()).isTrue();
        assertThat(mats.get("data").get("items").size()).isEqualTo(2);
        assertThat(mats.get("data").get("items").get(0).get("usageTexts").get(0).asText()).contains("×2");

        assertThat(get(token, "/cook/dish/999999/materials").get("code").asInt()).isNotEqualTo(0);

        JsonNode cook = post(token, "/cook/dish/" + DISH_FANQIE, Map.of("servings", 1));
        assertThat(cook.get("code").asInt()).as("cook msg=" + text(cook, "msg")).isEqualTo(0);
        JsonNode recent = get(token, "/cookbook/recent?limit=5");
        assertThat(recent.get("code").asInt()).isEqualTo(0);
        assertThat(recent.get("data").get(0).get("dishId").asLong()).isEqualTo(DISH_FANQIE);
        assertThat(recent.get("data").get(0).get("cookedCount").asInt()).isPositive();
        assertThat(recent.get("data").get(0).get("lastCookedAt").asText()).contains("T");
    }

    // ===================== S. 首页智荐（G5） =====================

    @Test
    void S_首页智荐_临期番茄_主推番茄炒蛋() {
        String token = loginAdmin();
        // 种子：番茄明天到期
        JsonNode home = get(token, "/recommend/home");
        assertThat(home.get("code").asInt()).as("home msg=" + text(home, "msg")).isEqualTo(0);
        JsonNode d = home.get("data");
        for (String k : List.of("expiring", "expiringPicks", "frequent", "seasonal")) {
            assertThat(d.get(k).isArray()).as(k + " 应为数组").isTrue();
        }
        boolean tomato = false;
        for (JsonNode e : d.get("expiring")) tomato |= e.get("ingredientId").asLong() == ING_TOMATO;
        assertThat(tomato).as("临期应含番茄").isTrue();
        assertThat(d.get("expiring").size()).isLessThanOrEqualTo(5);
        assertThat(d.get("expiringPicks").size()).isBetween(1, 3);
        // 主推优先「有封面 + 有步骤」的完整菜谱：staging 有大量带封面的导入菜谱，
        // 种子里无封面、无步骤的番茄炒蛋（DISH_FANQIE）不再进首推
        for (JsonNode c : d.get("expiringPicks")) {
            assertThat(c.has("coverage")).isTrue();
            assertThat(c.has("cookedCount")).isTrue();
            assertThat(c.get("dishId").asLong()).as("无封面无步骤的种子菜不进首推").isNotEqualTo(DISH_FANQIE);
            assertThat(c.get("coverUrl").isNull()).as("首推菜应有封面 " + c).isFalse();
        }
        assertThat(d.get("frequent").size()).isLessThanOrEqualTo(6);
        assertThat(d.get("seasonal").size()).isLessThanOrEqualTo(6);
    }

    // ===================== T. 个人资料（G7） =====================

    @Test
    void T_个人资料_读改_头像只收本站地址() {
        String token = loginAdmin();
        JsonNode me = get(token, "/member/me");
        assertThat(me.get("code").asInt()).as("me msg=" + text(me, "msg")).isEqualTo(0);
        String oldName = me.get("data").get("name").asText();
        assertThat(me.get("data").get("isAdmin").asBoolean()).isTrue();
        assertThat(me.get("data").has("phone")).isTrue();

        try {
            JsonNode up = put(token, "/member/me", Map.of("name", "掌勺改名测试", "avatarUrl", "/gudu/uploads/e2e/a.jpg"));
            assertThat(up.get("code").asInt()).as("put me msg=" + text(up, "msg")).isEqualTo(0);
            JsonNode me2 = get(token, "/member/me").get("data");
            assertThat(me2.get("name").asText()).isEqualTo("掌勺改名测试");
            assertThat(me2.get("avatarUrl").asText()).isEqualTo("/gudu/uploads/e2e/a.jpg");

            assertThat(put(token, "/member/me", Map.of("avatarUrl", "https://evil.com/a.jpg")).get("code").asInt())
                    .as("外链头像应拒绝").isNotEqualTo(0);
            assertThat(put(token, "/member/me", Map.of("name", "x".repeat(21))).get("code").asInt())
                    .as("超长昵称应拒绝").isNotEqualTo(0);
        } finally {
            put(token, "/member/me", Map.of("name", oldName, "avatarUrl", ""));
        }
        assertThat(get(token, "/member/me").get("data").get("name").asText()).isEqualTo(oldName);
    }

    // ===================== U. 意见反馈（G14） =====================

    @Test
    void U_意见反馈_提交_超管列表_改状态() {
        String token = loginAdmin();
        JsonNode bad = post(token, "/feedback", Map.of("content", "短"));
        assertThat(bad.get("code").asInt()).as("内容过短应拒绝").isNotEqualTo(0);

        JsonNode created = post(token, "/feedback", Map.of("content", "E2E反馈：希望增加菜谱导出",
                "contact", "e2e@example.com", "images", List.of("/gudu/uploads/e2e/f.jpg"), "clientInfo", "e2e"));
        assertThat(created.get("code").asInt()).as("feedback msg=" + text(created, "msg")).isEqualTo(0);
        long id = created.get("data").asLong();

        JsonNode list = get(token, "/feedback?status=NEW&pageNum=1&pageSize=10");
        assertThat(list.get("code").asInt()).isEqualTo(0);
        JsonNode mine = null;
        for (JsonNode f : list.get("data").get("records")) {
            if (f.get("id").asLong() == id) mine = f;
        }
        assertThat(mine).as("NEW 列表应含刚提交的反馈").isNotNull();
        assertThat(mine.get("images").get(0).asText()).isEqualTo("/gudu/uploads/e2e/f.jpg");
        assertThat(mine.get("memberName").isNull()).isFalse();

        assertThat(put(token, "/feedback/" + id + "/status", Map.of("status", "DONE")).get("code").asInt()).isEqualTo(0);
        boolean done = false;
        for (JsonNode f : get(token, "/feedback?status=DONE").get("data").get("records")) {
            done |= f.get("id").asLong() == id;
        }
        assertThat(done).isTrue();
    }

    @Test
    void U2_意见反馈闭环_类型来源_回复发站内消息_我的反馈可见() {
        String token = loginAdmin();
        assertThat(post(token, "/feedback", Map.of("content", "E2E反馈：类型非法", "type", "bug"))
                .get("code").asInt()).as("非法类型应拒绝").isNotEqualTo(0);

        JsonNode created = post(token, "/feedback", Map.of("content", "E2E反馈：AI 回答的做法不对",
                "type", "ai", "sourceRef", "agent:E2E-m1", "clientInfo", "e2e"));
        assertThat(created.get("code").asInt()).as("feedback msg=" + text(created, "msg")).isEqualTo(0);
        long id = created.get("data").asLong();

        // 超管按类型 + 状态筛选
        JsonNode ai = get(token, "/feedback?type=ai&status=NEW&pageSize=50").get("data").get("records");
        JsonNode row = null;
        for (JsonNode f : ai) {
            assertThat(f.get("type").asText()).isEqualTo("ai");
            if (f.get("id").asLong() == id) row = f;
        }
        assertThat(row).as("ai+NEW 筛选应含刚提交的反馈").isNotNull();
        assertThat(row.get("sourceRef").asText()).isEqualTo("agent:E2E-m1");
        for (JsonNode f : get(token, "/feedback?type=complaint").get("data").get("records")) {
            assertThat(f.get("id").asLong()).isNotEqualTo(id);
        }

        // 回复 → REPLIED + 提交人收到站内消息（ref 指向反馈）
        assertThat(put(token, "/feedback/" + id + "/reply", Map.of("reply", " ")).get("code").asInt())
                .as("空回复应拒绝").isNotEqualTo(0);
        assertThat(put(token, "/feedback/" + id + "/reply", Map.of("reply", "E2E回复：已修正菜谱")).get("code").asInt())
                .isEqualTo(0);
        JsonNode detail = get(token, "/feedback/mine/" + id).get("data");
        assertThat(detail.get("status").asText()).isEqualTo("REPLIED");
        assertThat(detail.get("reply").asText()).isEqualTo("E2E回复：已修正菜谱");
        assertThat(detail.get("repliedAt").isNull()).isFalse();
        boolean listed = false;
        for (JsonNode f : get(token, "/feedback/mine").get("data").get("records")) {
            listed |= f.get("id").asLong() == id;
        }
        assertThat(listed).as("我的反馈列表应含该条").isTrue();
        JsonNode notice = null;
        for (JsonNode n : get(token, "/notification").get("data")) {
            if ("feedback_reply".equals(text(n, "type")) && String.valueOf(id).equals(text(n, "refId"))) notice = n;
        }
        assertThat(notice).as("回复后应有 feedback_reply 站内消息").isNotNull();
        assertThat(text(notice, "content")).contains("已修正菜谱");

        // 回复并标记处理 → DONE
        assertThat(put(token, "/feedback/" + id + "/reply", Map.of("reply", "E2E回复：补充说明", "markDone", true))
                .get("code").asInt()).isEqualTo(0);
        assertThat(get(token, "/feedback/mine/" + id).get("data").get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void U3_个性化推荐开关_默认开_关闭后智荐与首屏只给通用推荐() {
        String token = loginAdmin();
        assertThat(get(token, "/preference").get("data").get("personalizedRecommend").asBoolean())
                .as("默认开启").isTrue();
        try {
            JsonNode off = put(token, "/preference", Map.of("personalizedRecommend", false));
            assertThat(off.get("code").asInt()).isEqualTo(0);
            assertThat(get(token, "/preference").get("data").get("personalizedRecommend").asBoolean()).isFalse();

            JsonNode home = get(token, "/recommend/home").get("data");
            assertThat(home.get("personalized").asBoolean()).isFalse();
            assertThat(home.get("expiring")).isEmpty();
            assertThat(home.get("expiringPicks")).isEmpty();
            assertThat(home.get("frequent")).isEmpty();

            JsonNode sug = get(token, "/agent/suggestions").get("data");
            assertThat(sug.get("expiring")).isEmpty();
            assertThat(text(sug, "greeting")).doesNotContain("，");
        } finally {
            put(token, "/preference", Map.of("personalizedRecommend", true));
        }
        assertThat(get(token, "/recommend/home").get("data").get("personalized").asBoolean()).isTrue();
    }

    // ===================== V. 运营仪表盘（G12） =====================

    @Test
    void V_运营仪表盘_超管统计() {
        String token = loginAdmin();
        JsonNode r = get(token, "/ops/stats?days=7");
        assertThat(r.get("code").asInt()).as("ops msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode d = r.get("data");
        assertThat(d.get("totals").get("members").asLong()).isPositive();
        assertThat(d.get("totals").get("accounts").asLong()).isPositive();
        assertThat(d.get("totals").get("dishes").asLong()).isPositive();
        assertThat(d.get("totals").has("feedbackOpen")).isTrue();
        assertThat(d.get("last7d").has("aiCostYuan")).isTrue();
        assertThat(d.get("trend").size()).isEqualTo(7);
        assertThat(d.get("trend").get(6).get("date").asText()).hasSize(10);
    }

    // ===================== W. 原型改版 P0（prototype-ui-rollout 0.2/0.3/0.7） =====================

    private static JsonNode byId(JsonNode arr, String idField, long id) {
        for (JsonNode n : arr) if (n.get(idField).asLong() == id) return n;
        return null;
    }

    @Test
    void W1_食材插画key_各接口透出() {
        String token = loginAdmin();
        // 食材库
        JsonNode ings = get(token, "/ingredient?keyword=番茄&pageNum=1&pageSize=50");
        assertThat(ings.get("code").asInt()).as("ingredient msg=" + text(ings, "msg")).isEqualTo(0);
        assertThat(byId(ings.get("data").get("records"), "id", ING_TOMATO).get("iconKey").asText()).isEqualTo("tomato");
        // 菜谱详情用料
        JsonNode detail = get(token, "/dish/" + DISH_FANQIE).get("data");
        JsonNode dishIngs = detail.has("ingredients") ? detail.get("ingredients") : detail.get("dish").get("ingredients");
        assertThat(byId(dishIngs, "ingredientId", ING_TOMATO).get("iconKey").asText()).isEqualTo("tomato");
        assertThat(byId(dishIngs, "ingredientId", 2L).get("iconKey").asText()).isEqualTo("egg");
        // 余量分组 / 详情 / 临期（种子：番茄明天到期）
        assertThat(byId(get(token, "/pantry/grouped").get("data").get("items"), "ingredientId", ING_TOMATO)
                .get("iconKey").asText()).isEqualTo("tomato");
        assertThat(get(token, "/pantry/item?ingredientId=" + ING_TOMATO).get("data").get("iconKey").asText()).isEqualTo("tomato");
        assertThat(byId(get(token, "/pantry/expiring?days=3").get("data"), "ingredientId", ING_TOMATO)
                .get("iconKey").asText()).isEqualTo("tomato");
        // 做菜确认弹窗
        JsonNode mats = get(token, "/cook/dish/" + DISH_FANQIE + "/materials").get("data").get("items");
        assertThat(byId(mats, "ingredientId", ING_TOMATO).get("iconKey").asText()).isEqualTo("tomato");
        // 采购：关联食材取库里 key；手动项按名称推断（纸巾→tissue）
        long listId = post(token, "/shopping/create", null).get("data").asLong();
        post(token, "/shopping/item/custom", Map.of("listId", listId, "name", "番茄", "amount", 1));
        post(token, "/shopping/item/custom", Map.of("listId", listId, "name", "E2E抽纸"));
        Map<String, String> iconByName = new HashMap<>();
        for (JsonNode it : get(token, "/shopping/" + listId).get("data").get("items")) {
            iconByName.put(it.get("ingredientName").asText(), it.get("iconKey").isNull() ? null : it.get("iconKey").asText());
        }
        assertThat(iconByName).containsEntry("番茄", "tomato").containsEntry("E2E抽纸", "tissue");
        delete(token, "/shopping/" + listId);
        // 新建食材自动算 key
        Map<String, Object> ing = new HashMap<>();
        ing.put("name", "E2E胡萝卜");
        ing.put("edible", 1);
        JsonNode created = post(token, "/ingredient", Map.of("ingredient", ing, "nutritions", List.of()));
        assertThat(created.get("code").asInt()).as("新建食材 msg=" + text(created, "msg")).isEqualTo(0);
        long newId = created.get("data").asLong();
        assertThat(get(token, "/ingredient/" + newId).get("data").get("iconKey").asText()).isEqualTo("carrot");
        delete(token, "/ingredient/" + newId);
    }

    @Test
    void W2_余量统计_我的统计_最近做菜人() {
        String token = loginAdmin();
        // 余量统计：种子番茄/鸡蛋均充足、番茄明天到期
        JsonNode sum = get(token, "/pantry/summary");
        assertThat(sum.get("code").asInt()).as("summary msg=" + text(sum, "msg")).isEqualTo(0);
        JsonNode sd = sum.get("data");
        assertThat(sd.get("enough").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(sd.get("expiring3d").asInt()).isGreaterThanOrEqualTo(1);
        int total = sd.get("enough").asInt() + sd.get("low").asInt() + sd.get("out").asInt();
        int byCat = 0;
        for (JsonNode c : sd.get("byCategory")) {
            byCat += c.get("count").asInt();
            assertThat(c.get("name").asText()).isNotBlank();
        }
        assertThat(byCat).as("分类计数之和 = 三档之和").isEqualTo(total);

        // 我的统计：做一次菜（用完明天到期的番茄）→ 做饭 +1、少扔 +1
        JsonNode before = get(token, "/me/stats");
        assertThat(before.get("code").asInt()).as("me/stats msg=" + text(before, "msg")).isEqualTo(0);
        JsonNode cook = post(token, "/cook/dish/" + DISH_FANQIE, Map.of("servings", 1, "usedUp", List.of(ING_TOMATO)));
        assertThat(cook.get("code").asInt()).as("cook msg=" + text(cook, "msg")).isEqualTo(0);
        JsonNode after = get(token, "/me/stats").get("data");
        assertThat(after.get("cookedThisMonth").asLong()).isEqualTo(before.get("data").get("cookedThisMonth").asLong() + 1);
        assertThat(after.get("savedFromWaste").asLong()).isEqualTo(before.get("data").get("savedFromWaste").asLong() + 1);
        assertThat(after.get("familyDishes").asLong()).isPositive();

        // 菜谱列表：最近一次做菜时间 + 做菜人
        JsonNode rec = byId(get(token, "/dish/search?keyword=番茄炒蛋&pageNum=1&pageSize=10").get("data").get("records"),
                "id", DISH_FANQIE);
        assertThat(rec.get("cookedCount").asInt()).isPositive();
        assertThat(rec.get("lastCookedAt").asText()).startsWith(LocalDate.now().toString());
        assertThat(rec.get("lastCookedBy").get("name").asText()).isNotBlank();
        assertThat(rec.get("lastCookedBy").has("avatarUrl")).isTrue();

        // 首页智荐菜卡 coverUrl 字段（与 coverImage 同值）
        JsonNode home = get(token, "/recommend/home").get("data");
        for (String k : List.of("expiringPicks", "frequent", "seasonal")) {
            for (JsonNode c : home.get(k)) {
                assertThat(c.has("coverUrl")).as(k + " 应带 coverUrl").isTrue();
                assertThat(c.get("coverUrl")).isEqualTo(c.get("coverImage"));
            }
        }
    }
    // ===================== X. 内容安全（mini-release-compliance A.6） =====================

    /**
     * test profile 关闭微信真实调用（gudu.content-security.wx-enabled=false），admin 为账号密码登录（无 openid）：
     * 文本走规则兜底；图片回调用签名推送模拟微信（自建 E2E 前缀 trace_id，seed 兜底清理）。
     */
    @Test
    void X_内容安全_昵称反馈规则兜底_图片违规回调_头像清空_帖子隐藏_占位图_对话输入拦截() throws Exception {
        String token = loginAdmin();

        // 1. 昵称命中禁发词：451，不保存
        String oldName = get(token, "/member/me").get("data").get("name").asText();
        JsonNode bad = put(token, "/member/me", Map.of("name", "网赌一哥"));
        assertThat(bad.get("code").asInt()).as("昵称违规 msg=" + text(bad, "msg")).isEqualTo(451);
        assertThat(text(bad, "msg")).contains("昵称").doesNotContain("网赌");
        assertThat(get(token, "/member/me").get("data").get("name").asText()).isEqualTo(oldName);

        // 2. 反馈命中禁发词：451，不落库；正常反馈不受影响
        JsonNode fb = post(token, "/feedback", Map.of("content", "E2E反馈：有人在评论区代开发票"));
        assertThat(fb.get("code").asInt()).as("反馈违规 msg=" + text(fb, "msg")).isEqualTo(451);
        assertThat(post(token, "/feedback", Map.of("content", "E2E反馈：希望增加菜谱导出")).get("code").asInt())
                .isEqualTo(0);

        // 3. 上传一张图，模拟已提交微信检测（trace_id E2E 前缀）
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() { return "e2e-cs.png"; }
        });
        form.add("usage", "avatar");
        JsonNode up = post(token, "/file/upload", form);
        assertThat(up.get("code").asInt()).as("上传 msg=" + text(up, "msg")).isEqualTo(0);
        String url = up.get("data").get("url").asText();
        String servedPath = url.replaceFirst("^/gudu", "");
        assertThat(http.getForEntity(CTX + servedPath, byte[].class).getStatusCode().value()).isEqualTo(200);
        String traceId = "E2E-" + java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO content_media_check(trace_id, media_url, member_id, purpose, status) "
                + "VALUES (?, ?, NULL, 'AVATAR', 'PENDING')", traceId, url);

        // 3b. 检测结果回来前：图已被用作头像、发进厨房圈（待回调的图片放行）
        String oldAvatar = text(get(token, "/member/me").get("data"), "avatarUrl");
        assertThat(put(token, "/member/me", Map.of("avatarUrl", url)).get("code").asInt()).isZero();
        assertThat(text(get(token, "/member/me").get("data"), "avatarUrl")).isEqualTo(url);
        Map<String, Object> postBody = new HashMap<>();
        postBody.put("title", "E2E内容安全帖");
        postBody.put("text", "E2E 正文：今天的番茄炒蛋");
        postBody.put("imageUrls", List.of(url));
        postBody.put("coverRatio", 0.75);
        JsonNode published = post(token, "/community/posts", postBody);
        assertThat(published.get("code").asInt()).as("发帖 msg=" + text(published, "msg")).isZero();
        long postId = published.get("data").get("postId").asLong();

        // 4. 微信消息推送：签名不对拒绝；签名正确 + risky → 定论 RISKY、订阅方处置、文件隔离
        String oldToken = wxProps.getToken();
        wxProps.setToken("e2e-push-token");
        try {
            String ts = String.valueOf(java.time.Instant.now().getEpochSecond());
            String pushBody = "{\"ToUserName\":\"gh_e2e\",\"FromUserName\":\"o_e2e\",\"CreateTime\":" + ts
                    + ",\"MsgType\":\"event\",\"Event\":\"wxa_media_check\",\"appid\":\"\",\"trace_id\":\""
                    + traceId + "\",\"version\":2,\"detail\":[],\"errcode\":0,\"errmsg\":\"ok\","
                    + "\"result\":{\"suggest\":\"risky\",\"label\":20002}}";
            assertThat(push("bad-sign", ts, "n1", pushBody)).isEqualTo("invalid signature");
            assertThat(jdbc.queryForObject("SELECT status FROM content_media_check WHERE trace_id = ?", String.class,
                    traceId)).isEqualTo("PENDING");
            String sign = com.gudu.xsd.modules.contentsecurity.domain.service.WxPushSignature.sign(
                    "e2e-push-token", ts, "n1");
            assertThat(push(sign, ts, "n1", pushBody)).isEqualTo("success");
            assertThat(push(sign, ts, "n1", pushBody)).as("重复推送幂等").isEqualTo("success");
        } finally {
            wxProps.setToken(oldToken);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM content_media_check WHERE trace_id = ?", String.class,
                traceId)).isEqualTo("RISKY");
        // 原地址换成占位图：仍 200（菜谱 / 食记等引用处不出现坏图），但已不是原图
        org.springframework.http.ResponseEntity<byte[]> served = http.getForEntity(CTX + servedPath, byte[].class);
        assertThat(served.getStatusCode().value()).as("原地址换占位图").isEqualTo(200);
        assertThat(served.getBody()).isNotEqualTo(png);
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(served.getBody())).getWidth())
                .as("占位图 600px").isEqualTo(600);

        // 头像订阅方：已清空（前端回退默认头像）
        assertThat(text(get(token, "/member/me").get("data"), "avatarUrl")).as("违规头像已清空").isEmpty();

        // 帖子订阅方：他人不可见；作者可见 HIDDEN + 「图片未通过审核」
        String chef = loginAs("13900000088", "chef123");
        assertThat(get(chef, "/community/posts/" + postId).get("code").asInt()).as("他人不可见").isNotZero();
        JsonNode mine = get(token, "/community/posts/" + postId);
        assertThat(mine.get("code").asInt()).as("作者可见 msg=" + text(mine, "msg")).isZero();
        assertThat(mine.get("data").get("status").asText()).isEqualTo("HIDDEN");
        assertThat(text(mine.get("data"), "statusReason")).isEqualTo("图片未通过审核");

        // 5. 再用违规图片当头像：451
        JsonNode avatar = put(token, "/member/me", Map.of("avatarUrl", url));
        assertThat(avatar.get("code").asInt()).as("头像违规 msg=" + text(avatar, "msg")).isEqualTo(451);
        assertThat(text(get(token, "/member/me").get("data"), "avatarUrl")).isNotEqualTo(url);
        if (!oldAvatar.isEmpty()) put(token, "/member/me", Map.of("avatarUrl", oldAvatar));

        // 6. 咕嘟对话：输入命中禁发词 → 不跑工具、回内容安全提示；落库的是占位文本而非原话
        JsonNode ses = post(token, "/agent/sessions", Map.of("title", "E2E内容安全"));
        assertThat(ses.get("code").asInt()).as(ses.toString()).isZero();
        String sessionId = ses.get("data").get("sessionId").asText();
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        String sse = http.exchange(CTX + "/agent/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(om.writeValueAsString(Map.of("text", "哪里能买到冰毒")), h), String.class).getBody();
        assertThat(sse).contains("event:meta").contains("event:done").doesNotContain("event:tool_start");
        assertThat(sse).contains("event:token");
        JsonNode msgs = get(token, "/agent/sessions/" + sessionId + "/messages");
        assertThat(msgs.get("code").asInt()).isZero();
        assertThat(msgs.get("data").toString()).doesNotContain("冰毒").contains("已屏蔽").contains("内容安全检测");
    }

    private String loginAs(String username, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        JsonNode r = post(null, "/auth/login", body);
        assertThat(r.get("code").asInt()).as("登录 " + username + " msg=" + text(r, "msg")).isEqualTo(0);
        return r.get("data").get("token").asText();
    }

    private String push(String signature, String timestamp, String nonce, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(CTX + "/wx/push?signature=" + signature + "&timestamp=" + timestamp + "&nonce=" + nonce,
                HttpMethod.POST, new HttpEntity<>(body, h), String.class).getBody();
    }
}
