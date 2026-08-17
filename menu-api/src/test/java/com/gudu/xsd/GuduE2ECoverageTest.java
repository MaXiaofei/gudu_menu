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
class GuduE2ECoverageTest {

    @Autowired
    private TestRestTemplate http;

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

        // provider 查询 + 设回 mock（幂等，防污染）
        JsonNode provider = get(token, "/ai/provider");
        assertThat(provider.get("code").asInt()).isEqualTo(0);
        assertThat(put(token, "/ai/provider", Map.of("provider", "mock")).get("code").asInt()).isEqualTo(0);
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
}
