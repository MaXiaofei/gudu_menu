package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产端到端：核心业务链路（改写自预发 GuduE2EFlowTest，AI 两个用例在 GuduProdE2EAiTest）。
 * 所有数据都在 E2E 超管家庭或自建账号 / 家庭内，类结束由基类按前缀清理。
 */
class GuduProdE2EFlowTest extends ProdE2EBase {

    // ---------------- 辅助 ----------------

    /** 设库存档位（超管家庭内）；bestBefore 为 null 则不传该键。 */
    private void setStock(String token, long ingredientId, String level, LocalDate bestBefore) {
        Map<String, Object> body = new HashMap<>();
        body.put("level", level);
        body.put("note", P + "库存准备");
        if (bestBefore != null) body.put("bestBefore", bestBefore.toString());
        JsonNode r = put(token, "/pantry/" + ingredientId + "/level", body);
        assertThat(r.get("code").asInt()).as("设库存 msg=" + text(r, "msg")).isEqualTo(0);
    }

    private long calorieMetricId() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM nutrition_metric WHERE name = 'calorie' ORDER BY id LIMIT 1", Long.class);
        assertThat(ids).as("生产里应有营养指标 calorie").isNotEmpty();
        return ids.get(0);
    }

    private long dictId(String token, String group, String name) {
        JsonNode r = get(token, "/dict?group=" + group + "&pageNum=1&pageSize=100");
        assertThat(r.get("code").asInt()).isEqualTo(0);
        for (JsonNode d : r.get("data").get("records")) {
            if (name.equals(d.get("name").asText())) return d.get("id").asLong();
        }
        return -1L;
    }

    // ---------------- 用例 ----------------

    /** 场景0（V29 合并）：手机号登录成功 + session.currentMemberId = 本人档案（合并核心）。 */
    @Test
    void 合并_手机号member登录成功_登录即定就餐成员() {
        JsonNode r = post(null, "/auth/login", Map.of("username", adminPhone, "password", PW));
        assertThat(r.get("code").asInt()).as("手机号登录应成功 msg=" + text(r, "msg")).isEqualTo(0);
        String token = r.get("data").get("token").asText();
        assertThat(token).as("应返回 token").isNotBlank();
        // 生产里没有「张爸爸」：昵称取自 E2E 超管的账号昵称
        assertThat(r.get("data").get("nickname").asText()).isEqualTo(P + "admin");

        // 合并核心：登录即定就餐成员，登录响应与 /member/current 都等于本人档案
        assertThat(r.get("data").get("currentMemberId").asLong()).isEqualTo(chefMemberId);
        JsonNode cur = get(token, "/member/current");
        assertThat(cur.get("code").asInt()).isEqualTo(0);
        assertThat(cur.get("data").asLong()).as("登录即定就餐成员：currentMemberId 应=本人档案").isEqualTo(chefMemberId);
    }

    /** 场景2（redesign）：从菜品生成采购草稿 → 用户填采购量+采购单位 → 验证保存。 */
    @Test
    void 采购_从菜品生成清单_用户填采购量保存() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + chefMemberId, null);

        JsonNode gen = post(token, "/shopping/generate", Map.of("sourceType", "dish", "sourceIds", List.of(dishId)));
        assertThat(gen.get("code").asInt()).as("生成采购 msg=" + text(gen, "msg")).isEqualTo(0);
        long listId = gen.get("data").asLong();
        assertThat(listId).isPositive();
        try {
            JsonNode detail = get(token, "/shopping/" + listId);
            assertThat(detail.get("code").asInt()).isEqualTo(0);
            JsonNode data = detail.get("data");
            assertThat(data.get("items").isArray()).isTrue();
            JsonNode tomato = null;
            for (JsonNode it : data.get("items")) {
                if ("番茄".equals(it.get("ingredientName").asText())) { tomato = it; break; }
            }
            assertThat(tomato).as("采购清单应含番茄").isNotNull();
            assertThat(tomato.get("referenceGrams").isNull())
                    .as("V55 后 referenceGrams 不再写入（列保留停用）").isTrue();
            assertThat(tomato.get("purchaseAmount").isNull()).as("草稿态采购量应为空").isTrue();

            long unitGe = dictId(token, "purchase_unit", "个");
            assertThat(unitGe).as("采购单位字典应含「个」").isPositive();

            long tomatoItemId = tomato.get("id").asLong();
            JsonNode updResp = put(token, "/shopping/item/" + tomatoItemId,
                    Map.of("purchaseAmount", 3, "purchaseUnitId", unitGe));
            assertThat(updResp.get("code").asInt()).isEqualTo(0);

            JsonNode detail2 = get(token, "/shopping/" + listId);
            JsonNode tomato2 = null;
            for (JsonNode it : detail2.get("data").get("items")) {
                if ("番茄".equals(it.get("ingredientName").asText())) { tomato2 = it; break; }
            }
            assertThat(tomato2).isNotNull();
            assertThat(tomato2.get("purchaseAmount").asDouble()).isEqualTo(3.0);
            assertThat(tomato2.get("purchaseUnitName").asText()).isEqualTo("个");

            long catVegetable = dictId(token, "purchase_category", "蔬菜");
            assertThat(catVegetable).as("采购品类字典应含「蔬菜」").isPositive();
            assertThat(detail2.get("data").get("grouped").has(Long.toString(catVegetable)))
                    .as("按品类分区应含「蔬菜」").isTrue();
        } finally {
            delete(token, "/shopping/" + listId);
        }
    }

    /** 场景3：自己录一条明天过期的番茄库存 → 手动触发（本家）临期扫描 → 掌勺成员收到 expiry 通知（前后差值）。 */
    @Test
    void 通知_录临期库存触发扫描_掌勺成员收到临期通知() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + chefMemberId, null);

        // 前置：接口录入明天到期的番茄库存（超管家庭内）
        setStock(token, tomatoId, "ENOUGH", LocalDate.now().plusDays(1));

        int before = countExpiry(token);

        // 手动触发临期扫描（不等 @Scheduled）；该接口只扫当前家庭
        JsonNode scan = post(token, "/notification/scan-expiring?days=3", null);
        assertThat(scan.get("code").asInt()).as("扫描不应报错 msg=" + text(scan, "msg")).isEqualTo(0);
        int sent = scan.get("data").get("sent").asInt();
        assertThat(sent).as("至少扫到 1 条临期库存").isGreaterThanOrEqualTo(1);

        int after = countExpiry(token);
        assertThat(after - before).as("有 sent 应多收到 expiry 通知（前后差值）").isGreaterThanOrEqualTo(1);
    }

    private int countExpiry(String token) {
        JsonNode list = get(token, "/notification");
        assertThat(list.get("code").asInt()).isEqualTo(0);
        int n = 0;
        for (JsonNode x : list.get("data")) {
            if ("expiry".equals(x.get("type").asText())) n++;
        }
        return n;
    }

    /** 场景4：提交饮食记录（番茄炒蛋 1 份）→ 营养汇总 calorie > 0。 */
    @Test
    void 饮食_记番茄炒蛋1份_营养汇总calorie大于0() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + chefMemberId, null);

        Map<String, Object> item = Map.of("dishId", dishId, "amount", 1, "servingFactor", 1);
        Map<String, Object> logReq = new HashMap<>();
        logReq.put("date", LocalDate.now().toString());
        logReq.put("note", P + "测试");
        logReq.put("items", List.of(item));

        JsonNode rs = post(token, "/dailylog", logReq);
        assertThat(rs.get("code").asInt()).as("记饮食 msg=" + text(rs, "msg")).isEqualTo(0);
        long logId = rs.get("data").asLong();
        assertThat(logId).isPositive();

        JsonNode nut = get(token, "/dailylog/" + logId + "/nutrition");
        assertThat(nut.get("code").asInt()).isEqualTo(0);
        JsonNode map = nut.get("data");
        assertThat(map).isNotNull();
        String key = Long.toString(calorieMetricId());
        assertThat(map.has(key)).as("营养汇总应含 calorie(metricId=" + key + ")").isTrue();
        double calorie = map.get(key).asDouble();
        assertThat(calorie).as("番茄炒蛋 1 份 calorie 应 > 0，实际=" + calorie).isGreaterThan(0);
    }

    /**
     * 场景5：无 dish.create 权限的普通成员账号录菜 → 被权限切面拒绝。
     * 自建账号以 MEMBER 身份加入超管家庭（家庭普通成员默认模板=34，无 dish.create），用其自己的 token 录菜。
     */
    @Test
    void 权限_普通成员录菜_被切面拒绝返回失败() {
        String admin = loginAdmin();
        JsonNode inv = post(admin, "/family/" + adminFamilyId + "/invitations", null);
        assertThat(inv.get("code").asInt()).as("邀请 msg=" + text(inv, "msg")).isEqualTo(0);
        String code = inv.get("data").get("code").asText();

        JsonNode normal = registerAndLogin(phoneFor("perm_member"), "普通成员");
        String token = normal.get("token").asText();
        JsonNode joined = post(token, "/family/join", Map.of("code", code));
        assertThat(joined.get("code").asInt()).as("加入 msg=" + text(joined, "msg")).isEqualTo(0);
        assertThat(joined.get("data").get("familyId").asLong()).isEqualTo(adminFamilyId);

        Map<String, Object> dish = new HashMap<>();
        dish.put("name", P + "权限测试菜");
        dish.put("difficulty", 1);
        Map<String, Object> saveReq = new HashMap<>();
        saveReq.put("dish", dish);
        saveReq.put("ingredients", List.of());
        saveReq.put("steps", List.of());

        JsonNode r = post(token, "/dish", saveReq);
        int code2 = r.get("code").asInt();
        assertThat(code2).as("普通成员录菜应被拒绝：code≠0，实际=" + code2).isNotZero();
        assertThat(text(r, "msg")).containsAnyOf("权限", "无此");
    }

    /** 场景8：建菜单 + 关联番茄炒蛋 + 汇总营养（V55：价格链路已删）。 */
    @Test
    void 菜单_建菜单关联番茄炒蛋_汇总营养() {
        String token = loginAdmin();

        Map<String, Object> menu = new HashMap<>();
        menu.put("name", P + "测试菜单");
        menu.put("servingCount", 2);
        Map<String, Object> md = new HashMap<>();
        md.put("dishId", dishId);
        md.put("servingFactor", 2);
        Map<String, Object> saveReq = new HashMap<>();
        saveReq.put("menu", menu);
        saveReq.put("dishes", List.of(md));

        JsonNode r = post(token, "/menu", saveReq);
        assertThat(r.get("code").asInt()).as("建菜单应成功 msg=" + text(r, "msg")).isEqualTo(0);
        long menuId = r.get("data").asLong();
        assertThat(menuId).as("应返回菜单 id").isPositive();
        try {
            JsonNode detail = get(token, "/menu/" + menuId);
            assertThat(detail.get("code").asInt()).isEqualTo(0);
            JsonNode d = detail.get("data");
            assertThat(d.get("menu").get("name").asText()).isEqualTo(P + "测试菜单");
            assertThat(d.get("dishes").isArray()).isTrue();
            assertThat(d.get("dishes").size()).isEqualTo(1);
            assertThat(d.get("dishes").get(0).get("dishId").asLong()).isEqualTo(dishId);

            JsonNode summary = get(token, "/menu/" + menuId + "/summary");
            assertThat(summary.get("code").asInt()).isEqualTo(0);
            JsonNode s = summary.get("data");
            String key = Long.toString(calorieMetricId());
            assertThat(s.get("totalNutrition").has(key)).as("菜单营养汇总应含 calorie(metricId=" + key + ")").isTrue();
            assertThat(s.get("totalNutrition").get(key).asDouble()).isGreaterThan(0);
        } finally {
            JsonNode del = delete(token, "/menu/" + menuId);
            assertThat(del.get("code").asInt()).isEqualTo(0);
        }
    }

    /** 场景9：member 分页查询，返回家庭成员列表（含本人档案）。 */
    @Test
    void 成员_分页查询_返回家庭成员列表() {
        String token = loginAdmin();

        JsonNode r = get(token, "/member?pageNum=1&pageSize=20");
        assertThat(r.get("code").asInt()).isEqualTo(0);
        JsonNode data = r.get("data");
        // 生产里 E2E 超管家庭只有本人一个档案（旧预发种子有 2 个成员）
        assertThat(data.get("total").asLong()).as("至少 1 个成员").isGreaterThanOrEqualTo(1);
        assertThat(data.get("records").isArray()).isTrue();
        boolean hasChef = false;
        for (JsonNode m : data.get("records")) {
            if (m.get("id").asLong() == chefMemberId) { hasChef = true; break; }
        }
        assertThat(hasChef).as("成员列表应含本人档案").isTrue();
    }

    /** 场景10：dict 分页查 unit 组，返回字典项分页结构（只读）。 */
    @Test
    void 字典_分页查unit组_返回字典项() {
        String token = loginAdmin();

        JsonNode r = get(token, "/dict?group=unit&pageNum=1&pageSize=20");
        assertThat(r.get("code").asInt()).isEqualTo(0);
        JsonNode data = r.get("data");
        assertThat(data.get("total").asLong()).as("unit 字典至少 1 项").isGreaterThanOrEqualTo(1);
        assertThat(data.get("records").isArray()).isTrue();
        for (JsonNode d : data.get("records")) {
            assertThat(d.get("dictGroup").asText()).isEqualTo("unit");
        }
    }

    /** 场景12（V36 Plan A）：建菜单 → 整集做菜 → 每菜写 cooking_record。 */
    @Test
    void 做菜_整集做菜扣库存_写record() {
        String token = loginAdmin();
        // 前置库存：番茄、鸡蛋各有货
        setStock(token, tomatoId, "ENOUGH", null);
        setStock(token, eggId, "ENOUGH", null);

        Map<String, Object> menu = new HashMap<>();
        menu.put("name", P + "做菜测试菜单");
        menu.put("servingCount", 1);
        Map<String, Object> md = new HashMap<>();
        md.put("dishId", dishId);
        md.put("servingFactor", 1);
        Map<String, Object> saveReq = new HashMap<>();
        saveReq.put("menu", menu);
        saveReq.put("dishes", List.of(md));

        JsonNode created = post(token, "/menu", saveReq);
        assertThat(created.get("code").asInt()).as("建菜单应成功 msg=" + text(created, "msg")).isEqualTo(0);
        long menuId = created.get("data").asLong();
        assertThat(menuId).as("应返回菜单 id").isPositive();
        try {
            JsonNode cook = post(token, "/menu/" + menuId + "/cook", null);
            assertThat(cook.get("code").asInt()).as("整集做菜应成功 msg=" + text(cook, "msg")).isEqualTo(0);
            JsonNode data = cook.get("data");
            assertThat(data.get("menuId").asLong()).isEqualTo(menuId);
            assertThat(data.get("cookingRecordIds").isArray()).isTrue();
            assertThat(data.get("cookingRecordIds").size()).as("每菜一条 cooking_record").isEqualTo(1);
        } finally {
            delete(token, "/menu/" + menuId);
        }
    }

    /**
     * 场景（feature-gaps-closure G1）：单菜直做全链路——弹窗数据 → 做菜（番茄用完 / 鸡蛋用了一些）
     * → 最近做过首条为该菜 → 库存档位变化（番茄 NONE 且保质期清除、鸡蛋 ENOUGH→LOW）→ 食记可见。
     * 前置：自己录入 番茄 ENOUGH(明天到期)、鸡蛋 ENOUGH。
     */
    @Test
    void 单菜直做_做完_最近做过可见_库存档位回写() {
        String token = loginAdmin();
        setStock(token, tomatoId, "ENOUGH", LocalDate.now().plusDays(1));
        setStock(token, eggId, "ENOUGH", null);

        JsonNode mats = get(token, "/cook/dish/" + dishId + "/materials");
        assertThat(mats.get("code").asInt()).as("materials msg=" + text(mats, "msg")).isEqualTo(0);
        boolean hasTomato = false;
        for (JsonNode it : mats.get("data").get("items")) {
            if (it.get("ingredientId").asLong() == tomatoId) {
                hasTomato = true;
                assertThat(it.get("level").asText()).isEqualTo("ENOUGH");
            }
        }
        assertThat(hasTomato).isTrue();

        Map<String, Object> body = new HashMap<>();
        body.put("servings", 1);
        body.put("usedUp", List.of(tomatoId));
        body.put("partiallyUsed", List.of(eggId));
        body.put("note", P + "单菜直做");
        JsonNode cook = post(token, "/cook/dish/" + dishId, body);
        assertThat(cook.get("code").asInt()).as("单菜直做 msg=" + text(cook, "msg")).isEqualTo(0);
        assertThat(cook.get("data").get("recordId").asLong()).isPositive();
        assertThat(cook.get("data").get("dishId").asLong()).isEqualTo(dishId);

        JsonNode recent = get(token, "/cookbook/recent?limit=10");
        assertThat(recent.get("code").asInt()).isEqualTo(0);
        assertThat(recent.get("data").get(0).get("dishId").asLong()).isEqualTo(dishId);

        JsonNode tomato = get(token, "/pantry/item?ingredientId=" + tomatoId).get("data");
        assertThat(tomato.get("level").asText()).isEqualTo("NONE");
        assertThat(tomato.get("bestBefore").isNull()).isTrue();
        assertThat(tomato.get("changes").get(0).get("action").asText()).isEqualTo("cook");
        assertThat(get(token, "/pantry/item?ingredientId=" + eggId).get("data").get("level").asText()).isEqualTo("LOW");
        boolean stillExpiring = false;
        for (JsonNode e : get(token, "/pantry/expiring?days=3").get("data")) {
            stillExpiring |= e.get("ingredientId").asLong() == tomatoId;
        }
        assertThat(stillExpiring).as("用完的番茄不应再出现在临期").isFalse();

        String ym = LocalDate.now().toString().substring(0, 7);
        JsonNode byDish = get(token, "/food-log/by-dish?month=" + ym);
        assertThat(byDish.get("code").asInt()).isEqualTo(0);
        boolean inLog = false;
        for (JsonNode it : byDish.get("data").get("items")) {
            inLog |= it.get("dishId").asLong() == dishId;
        }
        assertThat(inLog).as("食记应可见单菜直做").isTrue();
    }

    // ---------------- multi-tenant-family G.2：家庭主链路 + 账号合并 ----------------

    @Test
    void 家庭主链路_注册建家_邀请_加入_切换_退出() {
        // 注册：新账号自动建「我的家」并成为 OWNER
        JsonNode owner = registerAndLogin(phoneFor("fam_owner"), "家长");
        String ownerToken = owner.get("token").asText();
        long familyA = owner.get("currentFamilyId").asLong();
        assertThat(owner.get("families").get(0).get("role").asText()).isEqualTo("OWNER");
        assertThat(put(ownerToken, "/family/" + familyA, Map.of("name", P + "幸福一家")).get("code").asInt()).isZero();

        // 邀请：生成 7 天邀请码，游客可预览
        JsonNode inv = post(ownerToken, "/family/" + familyA + "/invitations", null);
        assertThat(inv.get("code").asInt()).isZero();
        String code = inv.get("data").get("code").asText();
        JsonNode preview = get(null, "/family/invitations/" + code);
        assertThat(preview.get("data").get("familyName").asText()).isEqualTo(P + "幸福一家");
        assertThat(preview.get("data").get("usable").asBoolean()).isTrue();

        // 加入：另一个账号凭码加入，当前家庭切到被邀请的家庭
        JsonNode kid = registerAndLogin(phoneFor("fam_kid"), "孩子");
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

        // 主理人不能退出
        assertThat(post(ownerToken, "/family/" + familyA + "/leave", null).get("code").asInt()).isNotZero();

        // 退出：成员退出后家庭只剩主理人，退出者回到自己的家
        assertThat(post(kidToken, "/family/" + familyA + "/leave", null).get("code").asInt()).isZero();
        assertThat(get(ownerToken, "/family/current").get("data").get("members")).hasSize(1);
        assertThat(get(kidToken, "/family/current").get("data").get("id").asLong()).isEqualTo(kidHome);
    }

    @Test
    void 账号合并_微信建号写数据_并入手机号账号_微信重新登录进入手机号账号且数据可见() {
        String pPhone = phoneFor("merge_p");
        String oid = P + "oid_merge_1";
        // 手机号账号 P（自己的家 F2）
        JsonNode p = registerAndLogin(pPhone, "老张");
        long pId = p.get("accountId").asLong();
        long f2 = p.get("currentFamilyId").asLong();

        // 微信账号 W：首次登录自动建号建家 F1，并写一条数据（食集）
        JsonNode w = wxLogin(oid, null);
        String wToken = w.get("token").asText();
        long f1 = w.get("currentFamilyId").asLong();
        assertThat(f1).isNotEqualTo(f2);
        JsonNode menu = post(wToken, "/menu", Map.of("menu", Map.of("name", P + "微信时期的食集", "servingCount", 1),
                "dishes", List.of()));
        assertThat(menu.get("code").asInt()).isZero();
        long menuId = menu.get("data").asLong();

        // 密码错误：拒绝，两个账号都不变
        assertThat(post(wToken, "/account/merge-phone", Map.of("phone", pPhone, "password", "wrong-pw"))
                .get("code").asInt()).isNotZero();

        // 合并：返回 P 的新 token，当前家庭沿用 F1
        JsonNode merged = post(wToken, "/account/merge-phone", Map.of("phone", pPhone, "password", PW));
        assertThat(merged.get("code").asInt()).as("合并 msg=" + text(merged, "msg")).isZero();
        assertThat(merged.get("data").get("accountId").asLong()).isEqualTo(pId);
        assertThat(merged.get("data").get("currentFamilyId").asLong()).isEqualTo(f1);
        assertThat(merged.get("data").get("families")).hasSize(2);

        // W 的旧 token 失效
        assertThat(get(wToken, "/account/me").get("code").asInt()).isEqualTo(401);

        // 微信重新登录 → 进入 P，F1 的数据可见
        JsonNode again = wxLogin(oid, null);
        assertThat(again.get("accountId").asLong()).isEqualTo(pId);
        String newToken = again.get("token").asText();
        assertThat(post(newToken, "/family/current", Map.of("familyId", f1)).get("code").asInt()).isZero();
        assertThat(get(newToken, "/menu/" + menuId).get("data").get("menu").get("name").asText())
                .isEqualTo(P + "微信时期的食集");
        // 手机号登录同样是 P，两个家庭都在
        JsonNode byPhone = post(null, "/auth/login", Map.of("username", pPhone, "password", PW));
        assertThat(byPhone.get("data").get("families")).hasSize(2);

        // 合并软删除（V98）：被并掉的微信账号 W 默认不在后台用户列表；显式筛「已合并」才可见，且个人信息已清空
        long wId = w.get("accountId").asLong();
        String admin = loginAdminScope();
        JsonNode normal = get(admin, "/admin/accounts?keyword=" + wId);
        assertThat(normal.get("code").asInt()).as(normal.toString()).isZero();
        for (JsonNode r : normal.get("data").get("records")) assertThat(r.get("id").asLong()).isNotEqualTo(wId);
        JsonNode mergedList = get(admin, "/admin/accounts?status=MERGED&keyword=" + wId);
        JsonNode wRow = mergedList.get("data").get("records").get(0);
        assertThat(wRow.get("id").asLong()).isEqualTo(wId);
        assertThat(wRow.get("nickname").isNull() || wRow.get("nickname").asText().isEmpty()).isTrue();
    }

    @Test
    void 账号合并_手机号账号已绑定其他微信_冲突_两个账号都不变() {
        String phone = phoneFor("conflict_p");
        registerAndLogin(phone, "已绑定");
        // 先让一个微信账号并入，使手机号账号绑定 openid-A
        String w1 = wxLogin(P + "oid_conflict_a", null).get("token").asText();
        assertThat(post(w1, "/account/merge-phone", Map.of("phone", phone, "password", PW))
                .get("code").asInt()).isZero();
        // 另一个微信账号再并入同一手机号账号 → ACCOUNT_WECHAT_CONFLICT
        JsonNode w2 = wxLogin(P + "oid_conflict_b", null);
        JsonNode r = post(w2.get("token").asText(), "/account/merge-phone", Map.of("phone", phone, "password", PW));
        assertThat(r.get("code").asInt()).isEqualTo(409);
        assertThat(text(r, "msg")).contains("ACCOUNT_WECHAT_CONFLICT");
        assertThat(get(w2.get("token").asText(), "/account/me").get("code").asInt()).as("W2 未失效").isZero();
    }

    @Test
    void 注销_有其他成员的主理人拒绝_普通成员注销后匿名化且token失效() {
        JsonNode owner = registerAndLogin(phoneFor("del_owner"), "注销主理人");
        String ownerToken = owner.get("token").asText();
        long family = owner.get("currentFamilyId").asLong();
        String code = post(ownerToken, "/family/" + family + "/invitations", null).get("data").get("code").asText();
        String memberPhone = phoneFor("del_member");
        JsonNode member = registerAndLogin(memberPhone, "注销成员");
        String memberToken = member.get("token").asText();
        assertThat(post(memberToken, "/family/join", Map.of("code", code)).get("code").asInt()).isZero();

        JsonNode denied = delete(ownerToken, "/account/me");
        assertThat(denied.get("code").asInt()).isNotZero();
        assertThat(text(denied, "msg")).contains("转让");

        // 被注销（匿名化）的账号已由 registerAndLogin 记台账，类结束按台账清理
        assertThat(delete(memberToken, "/account/me").get("code").asInt()).isZero();
        assertThat(get(memberToken, "/account/me").get("code").asInt()).isEqualTo(401);
        assertThat(post(null, "/auth/login", Map.of("username", memberPhone, "password", PW)).get("code").asInt())
                .isNotZero();
        JsonNode cur = get(ownerToken, "/family/current").get("data");
        assertThat(cur.get("members")).hasSize(1);
    }

    /** V55 P2-6：食材详情已删字段不再返回（反向断言，防字段回滚）。 */
    @Test
    void V55_食材详情_已删单位字段不返回() {
        String token = loginAdmin();
        JsonNode r = get(token, "/ingredient/" + tomatoId);
        assertThat(r.get("code").asInt()).as("食材详情应成功 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode d = r.get("data");
        assertThat(d.has("unitName")).as("ingredient.unitName 应已删（V55）").isFalse();
        assertThat(d.has("unitGramCount")).as("unitGramCount 应已删").isFalse();
        assertThat(d.has("defaultGramSet")).as("defaultGramSet 应已删").isFalse();
        assertThat(d.has("stockUnitName")).as("stockUnitName 应已删").isFalse();
        assertThat(d.has("price")).as("ingredient.price 应已删").isFalse();
    }
}
