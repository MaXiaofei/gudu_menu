package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产端到端：长尾接口覆盖（基础组）。由旧 GuduE2ECoverageTest 的 A~J、L 改写。
 * 全部用超管夹具家庭自建数据，名称带 E2E_PROD_ 前缀，用完即删，类结束由基类兜底清理。
 * 不含 K_AI估算（归 GuduProdE2EAiTest）。
 */
class GuduProdE2ECoverageBasicTest extends ProdE2EBase {

    private static final int OK = 0;

    private void useChef(String token) {
        assertThat(post(token, "/member/current?memberId=" + chefMemberId, null).get("code").asInt()).isEqualTo(OK);
    }

    private long dictId(String group, String name) {
        return jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_group = ? AND name = ? ORDER BY id LIMIT 1",
                Long.class, group, name);
    }

    // ===================== A. 食材完整 CRUD =====================

    @Test
    void A_食材CRUD_新建更新删除_无单位字段() {
        String token = loginAdmin();
        long cat = dictId("purchase_category", "蔬菜");

        // 家庭身份新建 -> 本家私有食材（营养被忽略）
        Map<String, Object> ing = new HashMap<>();
        ing.put("name", P + "菜心");
        ing.put("purchaseCategoryId", cat);
        ing.put("edible", 1);
        Map<String, Object> nut = Map.of("metricId", 1L, "value", 20);
        JsonNode created = post(token, "/ingredient", Map.of("ingredient", ing, "nutritions", List.of(nut)));
        assertThat(created.get("code").asInt()).as("新建食材 msg=" + text(created, "msg")).isEqualTo(OK);
        long id = created.get("data").asLong();
        assertThat(id).isPositive();
        try {
            JsonNode detail = get(token, "/ingredient/" + id);
            assertThat(detail.get("code").asInt()).isEqualTo(OK);
            JsonNode d = detail.get("data");
            assertThat(d.get("name").asText()).isEqualTo(P + "菜心");
            assertThat(d.has("unitName")).as("V55 已删 unitName").isFalse();

            // 更新食用属性
            Map<String, Object> upd = new HashMap<>(ing);
            upd.put("id", id);
            upd.put("edible", 3);
            assertThat(put(token, "/ingredient", upd).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/ingredient/" + id).get("data").get("edible").asInt()).isEqualTo(3);

            // 列表搜索命中
            JsonNode page = get(token, "/ingredient?keyword=" + P + "菜心&pageNum=1&pageSize=10");
            assertThat(page.get("data").get("total").asInt()).isEqualTo(1);
        } finally {
            // 删除（逻辑删）-> 详情 data 为 null
            assertThat(delete(token, "/ingredient/" + id).get("code").asInt()).isEqualTo(OK);
        }
        assertThat(get(token, "/ingredient/" + id).get("data").isNull()).isTrue();

        // 公共食材写（带营养）：后台身份，带前缀，用完删除
        String admin = loginAdminScope();
        Map<String, Object> pub = new HashMap<>();
        pub.put("name", P + "公共菜心");
        pub.put("purchaseCategoryId", cat);
        pub.put("edible", 1);
        JsonNode pc = post(admin, "/ingredient", Map.of("ingredient", pub, "nutritions", List.of(nut)));
        assertThat(pc.get("code").asInt()).as("新建公共食材 msg=" + text(pc, "msg")).isEqualTo(OK);
        long pubId = pc.get("data").asLong();
        try {
            JsonNode pd = get(admin, "/ingredient/" + pubId).get("data");
            assertThat(pd.get("name").asText()).isEqualTo(P + "公共菜心");
            assertThat(pd.get("nutrition").get("calorie").asDouble()).isEqualTo(20.0);
            assertThat(pd.has("unitName")).as("V55 已删 unitName").isFalse();
        } finally {
            assertThat(delete(admin, "/ingredient/" + pubId).get("code").asInt()).isEqualTo(OK);
        }
        assertThat(get(admin, "/ingredient/" + pubId).get("data").isNull()).isTrue();
    }

    // ===================== B. 菜谱 CRUD =====================

    @Test
    void B_菜谱CRUD_保存详情搜索更新删除() {
        String token = loginAdmin();

        Map<String, Object> dishBody = Map.of(
                "dish", Map.of("name", P + "覆盖菜", "difficulty", 2, "cookTime", 10),
                "ingredients", List.of(Map.of("ingredientId", tomatoId, "amount", 300, "unitId", unitGId)),
                "steps", List.of(Map.of("seq", 1, "text", P + "热锅下油")),
                "cuisineIds", List.of(), "tagIds", List.of(), "categoryIds", List.of());
        JsonNode created = post(token, "/dish", dishBody);
        assertThat(created.get("code").asInt()).as("保存菜谱 msg=" + text(created, "msg")).isEqualTo(OK);
        long id = created.get("data").asLong();
        try {
            JsonNode detail = get(token, "/dish/" + id);
            assertThat(detail.get("code").asInt()).isEqualTo(OK);
            JsonNode ing = detail.get("data").get("ingredients").get(0);
            assertThat(ing.get("unitName").asText()).isEqualTo("g");
            assertThat(ing.get("amount").asDouble()).isEqualTo(300.0);
            assertThat(ing.has("grams")).as("V55 已停用 grams").isFalse();

            JsonNode search = post(token, "/dish/search", Map.of("keyword", P + "覆盖", "pageNum", 1, "pageSize", 10));
            assertThat(search.get("code").asInt()).isEqualTo(OK);
            assertThat(search.get("data").get("total").asInt()).isEqualTo(1);

            JsonNode searchGet = get(token, "/dish/search?sort=cooked&pageNum=1&pageSize=5");
            assertThat(searchGet.get("code").asInt()).isEqualTo(OK);
            assertThat(searchGet.get("data").get("total").asInt()).isPositive();

            @SuppressWarnings("unchecked")
            Map<String, Object> updDish = new HashMap<>((Map<String, Object>) dishBody.get("dish"));
            updDish.put("id", id);
            updDish.put("name", P + "覆盖菜改");
            Map<String, Object> updBody = new HashMap<>(dishBody);
            updBody.put("dish", updDish);
            JsonNode updResp = put(token, "/dish", updBody);
            assertThat(updResp.get("code").asInt()).as("更新菜谱 msg=" + text(updResp, "msg")).isEqualTo(OK);
            assertThat(get(token, "/dish/" + id).get("data").get("dish").get("name").asText())
                    .isEqualTo(P + "覆盖菜改");

            JsonNode nutr = get(token, "/dish/" + id + "/nutrition?serving=1");
            assertThat(nutr.get("code").asInt()).isEqualTo(OK);
            assertThat(nutr.get("data").has("1")).isTrue();
        } finally {
            assertThat(delete(token, "/dish/" + id).get("code").asInt()).isEqualTo(OK);
        }
    }

    // ===================== C. 菜谱草稿 =====================

    @Test
    void C_菜谱草稿_建列表查改删() {
        String token = loginAdmin();
        int before = get(token, "/dish/draft/list?pageNum=1&pageSize=10").get("data").get("total").asInt();

        Map<String, Object> draft = Map.of(
                "name", P + "草稿菜",
                "ingredients", List.of(Map.of("ingredientId", tomatoId, "amount", "2", "unitText", "个")),
                "steps", List.of(Map.of("seq", 1, "text", P + "草稿步骤")));
        JsonNode created = post(token, "/dish/draft", draft);
        assertThat(created.get("code").asInt()).as("存草稿 msg=" + text(created, "msg")).isEqualTo(OK);
        long draftId = created.get("data").asLong();
        try {
            JsonNode list = get(token, "/dish/draft/list?pageNum=1&pageSize=10");
            assertThat(list.get("code").asInt()).isEqualTo(OK);
            assertThat(list.get("data").get("total").asInt()).isEqualTo(before + 1);

            JsonNode detail = get(token, "/dish/draft/" + draftId);
            assertThat(detail.get("code").asInt()).isEqualTo(OK);
            assertThat(detail.get("data").get("name").asText()).isEqualTo(P + "草稿菜");

            Map<String, Object> upd = new HashMap<>(draft);
            upd.put("id", draftId);
            upd.put("name", P + "草稿菜2");
            assertThat(post(token, "/dish/draft", upd).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/dish/draft/" + draftId).get("data").get("name").asText())
                    .isEqualTo(P + "草稿菜2");
        } finally {
            assertThat(delete(token, "/dish/draft/" + draftId).get("code").asInt()).isEqualTo(OK);
        }
        assertThat(get(token, "/dish/draft/list?pageNum=1&pageSize=10").get("data").get("total").asInt())
                .isEqualTo(before);
    }

    // ===================== D. 食集备菜 + 菜品管理 + 再做一次 =====================

    @Test
    void D_食集备菜状态_菜品备注移除_复制() {
        String token = loginAdmin();
        useChef(token);

        JsonNode menuCreated = post(token, "/menu", Map.of(
                "menu", Map.of("name", P + "备菜食集", "servingCount", 1),
                "dishes", List.of(Map.of("dishId", dishId, "servingFactor", 1))));
        assertThat(menuCreated.get("code").asInt()).as("建食集 msg=" + text(menuCreated, "msg")).isEqualTo(OK);
        long menuId = menuCreated.get("data").asLong();
        long copyId = -1;
        try {
            JsonNode prep = get(token, "/menu/" + menuId + "/prep");
            assertThat(prep.get("code").asInt()).isEqualTo(OK);
            assertThat(prep.get("data").get("totalCount").asInt()).isEqualTo(2); // 番茄+鸡蛋
            JsonNode firstItem = prep.get("data").get("items").get(0);
            assertThat(firstItem.get("usageTexts").size()).isPositive();
            assertThat(firstItem.get("status").asText()).isEqualTo("PENDING");
            long ingId = firstItem.get("ingredientId").asLong();

            assertThat(put(token, "/menu/" + menuId + "/prep/" + ingId + "?status=READY", null)
                    .get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/menu/" + menuId + "/prep").get("data").get("readyCount").asInt()).isEqualTo(1);

            assertThat(put(token, "/menu/" + menuId + "/dish/" + dishId + "/note", Map.of("note", "少辣"))
                    .get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/menu/" + menuId).get("data").get("dishes").get(0).get("note").asText())
                    .isEqualTo("少辣");

            JsonNode copied = post(token, "/menu/" + menuId + "/copy", null);
            assertThat(copied.get("code").asInt()).isEqualTo(OK);
            copyId = copied.get("data").asLong();
            JsonNode copyDetail = get(token, "/menu/" + copyId);
            assertThat(copyDetail.get("data").get("dishes").size()).isEqualTo(1);
            assertThat(copyDetail.get("data").get("menu").get("status").asText()).isEqualTo("ACTIVE");

            assertThat(delete(token, "/menu/" + menuId + "/dish/" + dishId).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/menu/" + menuId).get("data").get("dishes").size()).isEqualTo(0);
        } finally {
            delete(token, "/menu/" + menuId);
            if (copyId > 0) delete(token, "/menu/" + copyId);
        }
    }

    // ===================== E1. 采购：空清单 + 自定义项 + 改名 + 删 =====================

    @Test
    void E1_采购空清单_自定义项命中与未命中_改名删项删单() {
        String token = loginAdmin();

        JsonNode created = post(token, "/shopping/create", null);
        assertThat(created.get("code").asInt()).as("建空清单 msg=" + text(created, "msg")).isEqualTo(OK);
        long listId = created.get("data").asLong();
        long list2 = -1;
        try {
            JsonNode hit = post(token, "/shopping/item/custom", Map.of("listId", listId, "name", "番茄", "amount", 2));
            assertThat(hit.get("code").asInt()).isEqualTo(OK);

            JsonNode miss = post(token, "/shopping/item/custom", Map.of("listId", listId, "name", P + "自定义物品"));
            assertThat(miss.get("code").asInt()).isEqualTo(OK);
            long missId = miss.get("data").asLong();

            // V96：seq 为本家庭第几张；再建一张应为 seq+1
            int seq1 = get(token, "/shopping/" + listId).get("data").get("seq").asInt();
            assertThat(seq1).isGreaterThanOrEqualTo(1);
            list2 = post(token, "/shopping/create", null).get("data").asLong();
            assertThat(get(token, "/shopping/" + list2).get("data").get("seq").asInt()).isEqualTo(seq1 + 1);
            assertThat(delete(token, "/shopping/" + list2).get("code").asInt()).isEqualTo(OK);
            list2 = -1;

            JsonNode detail = get(token, "/shopping/" + listId);
            assertThat(detail.get("code").asInt()).isEqualTo(OK);
            assertThat(detail.get("data").get("items").size()).isEqualTo(2);
            boolean hasCustom = false;
            for (JsonNode it : detail.get("data").get("items")) {
                if ((P + "自定义物品").equals(it.get("ingredientName").asText())) hasCustom = true;
            }
            assertThat(hasCustom).as("未命中项应以 customName 展示").isTrue();

            assertThat(get(token, "/shopping/by-menu/999").get("data").isNull()).isTrue();

            JsonNode page = get(token, "/shopping?pageNum=1&pageSize=5");
            assertThat(page.get("code").asInt()).isEqualTo(OK);

            assertThat(put(token, "/shopping/" + listId + "/name", Map.of("name", P + "采购单"))
                    .get("code").asInt()).isEqualTo(OK);

            assertThat(delete(token, "/shopping/item/" + missId).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/shopping/" + listId).get("data").get("items").size()).isEqualTo(1);
        } finally {
            if (list2 > 0) delete(token, "/shopping/" + list2);
            assertThat(delete(token, "/shopping/" + listId).get("code").asInt()).isEqualTo(OK);
        }
    }

    // ===================== E2. 采购：勾选已买 + 批量入库 + 撤回 =====================

    @Test
    void E2_采购生成_勾选已买_批量入库_撤回() {
        String token = loginAdmin();
        useChef(token);

        JsonNode gen = post(token, "/shopping/generate", Map.of("sourceType", "dish", "sourceIds", List.of(dishId)));
        assertThat(gen.get("code").asInt()).as("生成清单 msg=" + text(gen, "msg")).isEqualTo(OK);
        long listId = gen.get("data").asLong();
        try {
            JsonNode detail = get(token, "/shopping/" + listId);
            assertThat(detail.get("data").get("items").size()).isEqualTo(2);

            long tomatoItemId = -1, eggItemId = -1;
            for (JsonNode it : detail.get("data").get("items")) {
                long ingId = it.get("ingredientId").asLong();
                if (ingId == tomatoId) tomatoItemId = it.get("id").asLong();
                else eggItemId = it.get("id").asLong();
            }
            assertThat(tomatoItemId).isPositive();
            assertThat(eggItemId).isPositive();

            assertThat(put(token, "/shopping/item/" + tomatoItemId + "/purchased", Map.of("level", "ENOUGH"))
                    .get("code").asInt()).isEqualTo(OK);
            for (JsonNode it : get(token, "/shopping/" + listId).get("data").get("items")) {
                if (it.get("id").asLong() == tomatoItemId) {
                    assertThat(it.get("purchased").asInt()).isEqualTo(1);
                }
            }

            JsonNode restock = post(token, "/shopping/restock", Map.of("itemIds", List.of(eggItemId)));
            assertThat(restock.get("code").asInt()).as("入库 msg=" + text(restock, "msg")).isEqualTo(OK);
            assertThat(restock.get("data").get("restocked").asInt()).isEqualTo(1);

            JsonNode fromPrep = post(token, "/shopping/from-prep",
                    Map.of("menuId", 999L, "ingredientIds", List.of(tomatoId)));
            assertThat(fromPrep.get("code").asInt()).isEqualTo(OK);

            assertThat(post(token, "/shopping/item/" + eggItemId + "/undo-restock", null)
                    .get("code").asInt()).isEqualTo(OK);
            for (JsonNode it : get(token, "/shopping/" + listId).get("data").get("items")) {
                assertThat(it.get("id").asLong()).as("撤回应删除该项").isNotEqualTo(eggItemId);
            }
        } finally {
            delete(token, "/shopping/" + listId);
            // 勾选已买时给超管夹具家庭的番茄建了档，清掉（鸡蛋档已被撤回）
            delete(token, "/pantry/" + tomatoId + "/level");
            delete(token, "/pantry/" + eggId + "/level");
        }
    }

    // ===================== F. 库存档位完整链路 =====================

    @Test
    void F_库存档位_手动入库改档查询删档_临期() {
        String token = loginAdmin();
        useChef(token);

        JsonNode manual = post(token, "/pantry/manual",
                Map.of("ingredientId", tomatoId, "level", "ENOUGH", "sourceNote", P + "入库"));
        assertThat(manual.get("code").asInt()).as("手动入库 msg=" + text(manual, "msg")).isEqualTo(OK);
        try {
            assertThat(put(token, "/pantry/" + tomatoId + "/level", Map.of("level", "LOW", "note", P + "改档"))
                    .get("code").asInt()).isEqualTo(OK);

            JsonNode grouped = get(token, "/pantry/grouped");
            assertThat(grouped.get("code").asInt()).isEqualTo(OK);
            assertThat(grouped.get("data").get("summary").get("low").asInt())
                    .as("改档后「不足」计数应 ≥1").isPositive();
            boolean inLow = false;
            for (JsonNode it : grouped.get("data").get("items")) {
                if (it.get("ingredientId").asLong() == tomatoId && "LOW".equals(it.get("level").asText())) {
                    inLow = true;
                }
            }
            assertThat(inLow).as("番茄应出现在列表且档位为 LOW").isTrue();

            JsonNode item = get(token, "/pantry/item?ingredientId=" + tomatoId);
            assertThat(item.get("code").asInt()).isEqualTo(OK);
            assertThat(item.get("data").get("level").asText()).isEqualTo("LOW");
            assertThat(item.get("data").get("changes").size()).isPositive();
        } finally {
            assertThat(delete(token, "/pantry/" + tomatoId + "/level").get("code").asInt()).isEqualTo(OK);
        }

        JsonNode expiring = get(token, "/pantry/expiring?days=3");
        assertThat(expiring.get("code").asInt()).isEqualTo(OK);
        assertThat(expiring.get("data").isArray()).isTrue();
    }

    // ===================== G. 评价 =====================

    @Test
    void G_评价_写菜评价均分我的_食集总览() {
        String token = loginAdmin();
        useChef(token);

        JsonNode created = post(token, "/review", Map.of("dishId", dishId, "starRating", 5, "text", P + "好吃"));
        assertThat(created.get("code").asInt()).as("写评价 msg=" + text(created, "msg")).isEqualTo(OK);

        JsonNode list = get(token, "/review/dish/" + dishId);
        assertThat(list.get("code").asInt()).isEqualTo(OK);
        assertThat(list.get("data").size()).isPositive();
        assertThat(list.get("data").get(0).get("text").asText()).isEqualTo(P + "好吃");

        JsonNode avg = get(token, "/review/dish/" + dishId + "/avg");
        assertThat(avg.get("code").asInt()).isEqualTo(OK);
        assertThat(avg.get("data").get("count").asInt()).isEqualTo(1);

        assertThat(get(token, "/review/mine").get("code").asInt()).isEqualTo(OK);

        JsonNode menuCreated = post(token, "/menu", Map.of(
                "menu", Map.of("name", P + "评价食集"),
                "dishes", List.of(Map.of("dishId", dishId, "servingFactor", 1))));
        assertThat(menuCreated.get("code").asInt()).isEqualTo(OK);
        long menuId = menuCreated.get("data").asLong();
        try {
            assertThat(get(token, "/review/menu-overview/" + menuId).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/review/menu/" + menuId).get("code").asInt()).isEqualTo(OK);
        } finally {
            delete(token, "/menu/" + menuId);
        }
    }

    // ===================== H. 收藏/按食材找菜 =====================

    @Test
    void H_收藏_加收藏列表按食材找菜取消() {
        String token = loginAdmin();

        assertThat(post(token, "/cookbook/favorite/" + dishId + "?memberId=" + chefMemberId, null)
                .get("code").asInt()).isEqualTo(OK);
        try {
            JsonNode favs = get(token, "/cookbook/favorites?memberId=" + chefMemberId);
            assertThat(favs.get("code").asInt()).isEqualTo(OK);
            boolean has = false;
            for (JsonNode d : favs.get("data")) {
                if (d.get("id").asLong() == dishId) has = true;
            }
            assertThat(has).as("收藏列表应含自建番茄炒蛋").isTrue();

            JsonNode match = get(token, "/cookbook/by-ingredients?ingredientIds=" + tomatoId);
            assertThat(match.get("code").asInt()).isEqualTo(OK);
            assertThat(match.get("data").size()).isPositive();
        } finally {
            assertThat(delete(token, "/cookbook/favorite/" + dishId + "?memberId=" + chefMemberId)
                    .get("code").asInt()).isEqualTo(OK);
        }
        assertThat(get(token, "/cookbook/favorites?memberId=" + chefMemberId).get("data").size()).isEqualTo(0);
    }

    // ===================== I. 食记 food-log + 做菜历史 =====================

    @Test
    void I_食记_做菜写记录_月年按菜明细_做菜历史() {
        String token = loginAdmin();
        useChef(token);

        JsonNode menuCreated = post(token, "/menu", Map.of(
                "menu", Map.of("name", P + "食记食集"),
                "dishes", List.of(Map.of("dishId", dishId, "servingFactor", 1))));
        assertThat(menuCreated.get("code").asInt()).isEqualTo(OK);
        long menuId = menuCreated.get("data").asLong();
        try {
            JsonNode cook = post(token, "/menu/" + menuId + "/cook",
                    Map.of("usedUp", List.of(tomatoId), "partiallyUsed", List.of()));
            assertThat(cook.get("code").asInt()).as("做菜 msg=" + text(cook, "msg")).isEqualTo(OK);

            String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));

            assertThat(get(token, "/food-log/month?month=" + month).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/food-log/by-dish?month=" + month).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/food-log/year?year=" + year).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/food-log/detail?menuId=" + menuId).get("code").asInt()).isEqualTo(OK);
            assertThat(get(token, "/dish/" + dishId + "/history").get("code").asInt()).isEqualTo(OK);
        } finally {
            delete(token, "/menu/" + menuId);
        }
    }

    // ===================== J. 通知 =====================

    @Test
    void J_通知_扫描临期_列表已读未读数() {
        String token = loginAdmin();
        useChef(token);

        // 前置：超管夹具家庭的番茄明天过期
        String tomorrow = LocalDate.now().plusDays(1).toString();
        JsonNode manual = post(token, "/pantry/manual", Map.of("ingredientId", tomatoId, "level", "ENOUGH",
                "sourceNote", P + "临期番茄", "bestBefore", tomorrow));
        assertThat(manual.get("code").asInt()).as("录临期库存 msg=" + text(manual, "msg")).isEqualTo(OK);
        try {
            // 扫描只作用于当前（超管夹具）家庭，通知掌勺成员
            JsonNode scan = post(token, "/notification/scan-expiring?days=3", null);
            assertThat(scan.get("code").asInt()).isEqualTo(OK);
            assertThat(scan.get("data").get("sent").asInt()).as("应发出临期提醒").isPositive();

            JsonNode list = get(token, "/notification");
            assertThat(list.get("code").asInt()).isEqualTo(OK);
            JsonNode items = list.get("data");
            assertThat(items.isArray() && items.size() > 0).as("临期扫描后应有通知").isTrue();

            JsonNode unread = get(token, "/notification/unread-count");
            int beforeCount = unread.get("data").get("count").asInt();
            assertThat(beforeCount).isPositive();

            long firstId = items.get(0).get("id").asLong();
            assertThat(put(token, "/notification/" + firstId + "/read", null).get("code").asInt()).isEqualTo(OK);
            int afterCount = get(token, "/notification/unread-count").get("data").get("count").asInt();
            assertThat(afterCount).isEqualTo(beforeCount - 1);
        } finally {
            delete(token, "/pantry/" + tomatoId + "/level");
        }
    }

    // ===================== L. member/auth 剩余接口 =====================

    @Test
    void L_成员auth_建成员改查权限营养目标_删除_登出() {
        String token = loginAdmin();
        String phone = phoneFor("l_member");

        JsonNode created = post(token, "/member", Map.of("name", P + "成员", "phone", phone, "password", PW));
        assertThat(created.get("code").asInt()).as("建成员 msg=" + text(created, "msg")).isEqualTo(OK);
        long memberId = created.get("data").asLong();
        // 代建的账号登记台账（清理兜底，只记刚建出来的档案关联的账号）
        jdbc.queryForList("SELECT account_id FROM member WHERE id = ? AND account_id IS NOT NULL", Long.class, memberId)
                .forEach(ProdE2ELedger::recordAccount);

        Map<String, Object> upd = new HashMap<>();
        upd.put("id", memberId);
        upd.put("name", P + "成员改");
        assertThat(put(token, "/member", upd).get("code").asInt()).isEqualTo(OK);

        JsonNode perms = get(token, "/member/" + memberId + "/permissions");
        assertThat(perms.get("code").asInt()).isEqualTo(OK);
        assertThat(perms.get("data").isArray()).isTrue();
        assertThat(get(token, "/member/permissions/keys").get("code").asInt()).isEqualTo(OK);

        assertThat(get(token, "/member/" + memberId + "/nutrition-target").get("code").asInt()).isEqualTo(OK);

        assertThat(get(token, "/auth/me").get("code").asInt()).isEqualTo(OK);

        // 带手机号的成员 = 有登录账号：不能走档案删除，必须拒绝
        assertThat(delete(token, "/member/" + memberId).get("code").asInt())
                .as("有账号成员不可经 DELETE /member 删除").isEqualTo(403);
        // 纯档案成员可删
        JsonNode profileOnly = post(token, "/member", Map.of("name", P + "档案成员"));
        assertThat(profileOnly.get("code").asInt()).as("建纯档案成员 msg=" + text(profileOnly, "msg")).isEqualTo(OK);
        assertThat(delete(token, "/member/" + profileOnly.get("data").asLong()).get("code").asInt()).isEqualTo(OK);

        // 登出 -> 旧 token 失效
        assertThat(post(token, "/auth/logout", null).get("code").asInt()).isEqualTo(OK);
        assertThat(get(token, "/auth/me").get("code").asInt()).as("登出后旧 token 应失效").isNotEqualTo(OK);
    }
}
