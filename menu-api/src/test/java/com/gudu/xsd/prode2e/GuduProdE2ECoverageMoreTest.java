package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudu.xsd.common.upload.UploadUrlSigner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产端到端：接口覆盖补全（第二组）——字典与指标、普通用户越权、一起吃、文件上传、保质期、菜谱覆盖率、
 * 常用食材/最近做过、首页智荐、个人资料、意见反馈、个性化推荐开关、运营仪表盘/健康摘要、食材插画 key、余量统计。
 *
 * <p>改写自旧预发 GuduE2ECoverageTest（N～W2）。每个用例自己准备前置条件、自建数据（E2E_PROD_ 前缀）；
 * 全站只读汇总类接口（运营仪表盘、健康摘要）只断言结构；会改全站表的用例（公共食材/字典/指标）用完在 finally 里按 id 精确删除。
 */
class GuduProdE2ECoverageMoreTest extends ProdE2EBase {

    @Autowired
    private UploadUrlSigner uploadSigner;

    @Value("${gudu.upload-dir}")
    private String uploadDir;

    private static final String PNG_B64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static String unsigned(String url) {
        return UploadUrlSigner.strip(url);
    }

    private static JsonNode byId(JsonNode arr, String idField, long id) {
        if (arr == null) return null;
        for (JsonNode n : arr) if (n.get(idField).asLong() == id) return n;
        return null;
    }

    /** 设余量档位（可带保质期）。 */
    private void setLevel(String token, long ingredientId, String level, LocalDate bestBefore) {
        Map<String, Object> body = new HashMap<>();
        body.put("level", level);
        if (bestBefore != null) body.put("bestBefore", bestBefore.toString());
        JsonNode r = put(token, "/pantry/" + ingredientId + "/level", body);
        assertThat(r.get("code").asInt()).as("设档 msg=" + text(r, "msg")).isEqualTo(0);
    }

    /** 运营列表可能含真实反馈：翻页找到自建的那一条（最多 30 页）。 */
    private JsonNode findFeedback(String operator, String query, long id) {
        for (int page = 1; page <= 30; page++) {
            JsonNode r = get(operator, "/feedback?" + query + (query.isEmpty() ? "" : "&") + "pageNum=" + page + "&pageSize=100");
            assertThat(r.get("code").asInt()).as("反馈列表 msg=" + text(r, "msg")).isEqualTo(0);
            JsonNode records = r.get("data").get("records");
            JsonNode hit = byId(records, "id", id);
            if (hit != null) return hit;
            if (records.size() < 100) break;
        }
        return null;
    }

    // ===================== N. 字典 + 营养指标 CRUD =====================

    @Test
    void N_字典与指标_自建改删() {
        String token = loginAdminScope();
        long dictId = -1;
        long metricId = -1;
        try {
            // 字典：建（sort NOT NULL 必带；unit 字典线上在用，自建测试项用完即删）
            JsonNode dictCreated = post(token, "/dict", Map.of("name", P + "单位", "dictGroup", "unit", "sort", 0));
            assertThat(dictCreated.get("code").asInt()).as(dictCreated.toString()).isEqualTo(0);
            dictId = dictCreated.get("data").asLong();
            // 改
            Map<String, Object> dictUpd = new HashMap<>();
            dictUpd.put("id", dictId);
            dictUpd.put("name", P + "单位改");
            dictUpd.put("dictGroup", "unit");
            assertThat(put(token, "/dict", dictUpd).get("code").asInt()).isEqualTo(0);
            // 删
            assertThat(delete(token, "/dict/" + dictId).get("code").asInt()).isEqualTo(0);
            dictId = -1;

            // 指标：建改删（metric_group NOT NULL 必带）
            JsonNode metricCreated = post(token, "/nutrition/metric",
                    Map.of("name", P + "metric", "unit", "mg", "metricGroup", "micro", "sort", 0));
            assertThat(metricCreated.get("code").asInt()).as(metricCreated.toString()).isEqualTo(0);
            metricId = metricCreated.get("data").asLong();
            Map<String, Object> mUpd = new HashMap<>();
            mUpd.put("id", metricId);
            mUpd.put("name", P + "metric2");
            mUpd.put("unit", "mg");
            assertThat(put(token, "/nutrition/metric", mUpd).get("code").asInt()).isEqualTo(0);
            // 全量列表
            JsonNode all = get(token, "/nutrition/metric");
            assertThat(all.get("code").asInt()).isEqualTo(0);
            assertThat(byId(all.get("data"), "id", metricId)).as("全量列表应含自建指标").isNotNull();
            assertThat(delete(token, "/nutrition/metric/" + metricId).get("code").asInt()).isEqualTo(0);
            metricId = -1;
        } finally {
            if (dictId > 0) delete(token, "/dict/" + dictId);
            if (metricId > 0) delete(token, "/nutrition/metric/" + metricId);
        }
    }

    /**
     * 与旧用例的差异：旧用例拿种子里的番茄 / 字典 1 / 指标 1 当「全局被保护对象」去尝试越权改删；
     * 生产里这些是真实公共数据，万一守卫失效会误改误删，所以改成运营（后台身份）先自建一套 E2E_PROD_ 公共食材 / 字典 / 指标，
     * 再让普通用户去改删它们（断言同为 403）。
     */
    @Test
    void N2_普通用户不能改删全局食材_字典_指标_仅可受限新建() {
        String adminBo = loginAdminScope();
        String userPhone = phoneFor("n2user");
        createAccount(userPhone, P + "普通用户");
        String user = loginAs(userPhone, PW);

        long pubIng = -1;
        long pubDict = -1;
        long pubMetric = -1;
        long aliasId = -1;
        long catId = -1;
        long privIng = -1;
        try {
            // 运营后台身份建公共食材 / 字典 / 指标（被保护对象）
            JsonNode pi = post(adminBo, "/ingredient", Map.of("ingredient",
                    Map.of("name", P + "公共食材N2", "edible", 1), "nutritions", List.of()));
            assertThat(pi.get("code").asInt()).as(pi.toString()).isZero();
            pubIng = pi.get("data").asLong();
            JsonNode pd = post(adminBo, "/dict", Map.of("name", P + "保护字典", "dictGroup", "unit", "sort", 0));
            assertThat(pd.get("code").asInt()).as(pd.toString()).isZero();
            pubDict = pd.get("data").asLong();
            JsonNode pm = post(adminBo, "/nutrition/metric",
                    Map.of("name", P + "保护指标", "unit", "mg", "metricGroup", "micro", "sort", 0));
            assertThat(pm.get("code").asInt()).as(pm.toString()).isZero();
            pubMetric = pm.get("data").asLong();

            // 改 / 删全局食材、字典、营养指标 → 403
            Map<String, Object> ingUpd = new HashMap<>();
            ingUpd.put("id", pubIng);
            ingUpd.put("edible", 3);
            assertThat(put(user, "/ingredient", ingUpd).get("code").asInt()).isEqualTo(403);
            assertThat(delete(user, "/ingredient/" + pubIng).get("code").asInt()).isEqualTo(403);
            assertThat(get(user, "/ingredient/" + pubIng).get("data").get("edible").asInt()).isEqualTo(1);
            assertThat(put(user, "/dict", Map.of("id", pubDict, "dictGroup", "unit", "name", P + "被改")).get("code").asInt())
                    .isEqualTo(403);
            assertThat(delete(user, "/dict/" + pubDict).get("code").asInt()).isEqualTo(403);
            assertThat(put(user, "/nutrition/metric", Map.of("id", pubMetric, "name", P + "被改指标", "unit", "mg"))
                    .get("code").asInt()).isEqualTo(403);
            assertThat(delete(user, "/nutrition/metric/" + pubMetric).get("code").asInt()).isEqualTo(403);
            assertThat(post(user, "/nutrition/metric", Map.of("name", P + "x", "unit", "mg", "metricGroup", "micro",
                    "sort", 0)).get("code").asInt()).isEqualTo(403);

            // POST /dict：非 purchase_category 403；purchase_category 可新增，同名返回同一 id
            assertThat(post(user, "/dict", Map.of("name", P + "普通单位", "dictGroup", "unit", "sort", 0))
                    .get("code").asInt()).isEqualTo(403);
            JsonNode c1 = post(user, "/dict", Map.of("name", P + "普通分类", "dictGroup", "purchase_category", "sort", 0));
            assertThat(c1.get("code").asInt()).as(c1.toString()).isZero();
            catId = c1.get("data").asLong();
            JsonNode c2 = post(user, "/dict", Map.of("name", P + "普通分类", "dictGroup", "purchase_category", "sort", 0));
            assertThat(c2.get("data").asLong()).isEqualTo(catId);

            // POST /ingredient：带 id 不会覆盖公共食材；营养被忽略；同名返回已有
            Map<String, Object> ing = new HashMap<>();
            ing.put("id", pubIng);
            ing.put("name", P + "普通食材");
            ing.put("purchaseCategoryId", catId);
            ing.put("edible", 1);
            Map<String, Object> nut = Map.of("metricId", pubMetric, "value", 99);
            JsonNode created = post(user, "/ingredient", Map.of("ingredient", ing, "nutritions", List.of(nut)));
            assertThat(created.get("code").asInt()).as(created.toString()).isZero();
            privIng = created.get("data").asLong();
            assertThat(privIng).isNotEqualTo(pubIng);
            assertThat(get(user, "/ingredient/" + pubIng).get("data").get("name").asText()).isEqualTo(P + "公共食材N2");
            assertThat(get(user, "/ingredient/" + privIng).get("data").get("nutrition").size()).isZero();
            JsonNode again = post(user, "/ingredient", Map.of("ingredient", ing, "nutritions", List.of()));
            assertThat(again.get("data").asLong()).isEqualTo(privIng);

            // V95 家庭私有食材：新建的是本家私有（带 familyOwned），只看我家能筛出来
            JsonNode mine = get(user, "/ingredient?familyOnly=true&keyword=" + P + "普通食材&pageNum=1&pageSize=10");
            assertThat(mine.get("data").get("records").size()).isEqualTo(1);
            assertThat(mine.get("data").get("records").get(0).get("familyOwned").asBoolean()).isTrue();
            // 本家成员可改名（与公共名重名被拒），公共食材仍 403
            Map<String, Object> rename = new HashMap<>();
            rename.put("id", privIng);
            rename.put("name", P + "公共食材N2");
            assertThat(put(user, "/ingredient", rename).get("code").asInt()).isNotZero();
            rename.put("name", P + "普通食材改");
            assertThat(put(user, "/ingredient", rename).get("code").asInt()).isZero();

            // 公共食材别名 / 收编属平台维护，须管理后台身份（scope=admin）
            // 运营加别名：搜别名能搜到公共食材；普通用户用别名新建 -> 返回该公共食材 id
            JsonNode al = post(adminBo, "/admin/ingredient-adoption/aliases",
                    Map.of("alias", P + "别名N2", "ingredientId", pubIng));
            assertThat(al.get("code").asInt()).as(al.toString()).isZero();
            aliasId = al.get("data").get("id").asLong();
            JsonNode hit = get(user, "/ingredient?keyword=" + P + "别名N2&pageNum=1&pageSize=10");
            assertThat(hit.get("data").get("records").get(0).get("id").asLong()).isEqualTo(pubIng);
            Map<String, Object> viaAlias = new HashMap<>();
            viaAlias.put("name", P + "别名N2");
            JsonNode resolved = post(user, "/ingredient/resolve", viaAlias);
            assertThat(resolved.get("data").get("id").asLong()).isEqualTo(pubIng);
            assertThat(resolved.get("data").get("existed").asText()).isEqualTo("public");
            assertThat(delete(adminBo, "/admin/ingredient-adoption/aliases/" + aliasId).get("code").asInt()).isZero();
            aliasId = -1;
            // 运营收编汇总可读；未使用的私有食材可删
            assertThat(get(adminBo, "/admin/ingredient-adoption/groups").get("code").asInt()).isZero();
            assertThat(delete(user, "/ingredient/" + privIng).get("code").asInt()).isZero();
            privIng = -1;
        } finally {
            if (aliasId > 0) delete(adminBo, "/admin/ingredient-adoption/aliases/" + aliasId);
            if (privIng > 0) delete(user, "/ingredient/" + privIng);
            if (pubIng > 0) delete(adminBo, "/ingredient/" + pubIng);
            if (pubDict > 0) delete(adminBo, "/dict/" + pubDict);
            if (catId > 0) delete(adminBo, "/dict/" + catId);
            if (pubMetric > 0) delete(adminBo, "/nutrition/metric/" + pubMetric);
        }
    }

    // ===================== O. 一起吃（协同点菜） =====================

    @Test
    void O_一起吃_建邀请口令token_加入_点菜_改名_搜索_删菜() {
        String token = loginAdmin();
        post(token, "/member/current?memberId=" + chefMemberId, null);

        // 自建食集
        long menuId = post(token, "/menu", Map.of(
                "menu", Map.of("name", P + "一起吃食集"), "dishes", List.of()))
                .get("data").asLong();
        try {
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
            JsonNode join = post(token, "/invite/" + inviteToken + "/join", Map.of("nickname", P + "食客"));
            assertThat(join.get("code").asInt()).isEqualTo(0);

            // 协同页（成员+菜+动态）
            JsonNode together = get(token, "/menu/" + menuId + "/together");
            assertThat(together.get("code").asInt()).isEqualTo(0);

            // 协同点菜（白名单接口，登录成员身份）
            JsonNode addItem = post(token, "/menu/" + menuId + "/together/items", Map.of("dishId", dishId));
            assertThat(addItem.get("code").asInt()).as("协同点菜 msg=" + text(addItem, "msg")).isEqualTo(0);

            // 菜搜索（须本家成员或已凭邀请加入者；未登录无身份被拒）
            assertThat(get(null, "/menu/" + menuId + "/together/dishes?keyword=番茄").get("code").asInt())
                    .isNotEqualTo(0);
            assertThat(get(token, "/menu/" + menuId + "/together/dishes?keyword=番茄").get("code").asInt())
                    .isEqualTo(0);

            // 改昵称
            assertThat(put(token, "/menu/" + menuId + "/together/nickname", Map.of("nickname", P + "食客改"))
                    .get("code").asInt()).isEqualTo(0);

            // together-count（占位）
            assertThat(get(token, "/menu/" + menuId + "/together-count").get("code").asInt()).isEqualTo(0);

            // 删协同菜（取加入项的 id）
            JsonNode together2 = get(token, "/menu/" + menuId + "/together");
            long menuDishId = -1;
            if (together2.get("data").has("dishes") && together2.get("data").get("dishes").isArray()) {
                for (JsonNode d : together2.get("data").get("dishes")) {
                    if (d.has("id")) {
                        menuDishId = d.get("id").asLong();
                        break;
                    }
                }
            }
            if (menuDishId > 0) {
                assertThat(delete(token, "/menu/" + menuId + "/together/items/" + menuDishId)
                        .get("code").asInt()).isEqualTo(0);
            }
        } finally {
            delete(token, "/menu/" + menuId);
        }
    }

    // ===================== Q. 文件上传（multipart） =====================

    private MultiValueMap<String, Object> pngForm(String filename) {
        byte[] png = java.util.Base64.getDecoder().decode(PNG_B64);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return form;
    }

    @Test
    void Q_文件上传_小图返回URL与缩略图() {
        String token = loginAdmin();
        JsonNode up = post(token, "/file/upload", pngForm("e2e-prod-test.png"));
        assertThat(up.get("code").asInt()).as("上传 msg=" + text(up, "msg")).isEqualTo(0);
        JsonNode data = up.get("data");
        assertThat(data.get("url").asText()).contains("/uploads/");
        assertThat(data.has("thumbnailUrl")).isTrue();
    }

    // ===================== Q2. 上传文件访问控制（UUID 文件名 + 短时签名 URL） =====================

    @Test
    void Q2_上传文件_UUID文件名_签名可读_未签名或篡改403_入参未签名拒绝() {
        String token = loginAdmin();
        byte[] png = java.util.Base64.getDecoder().decode(PNG_B64);
        JsonNode up = post(token, "/file/upload", pngForm("e2e-prod-sign.png"));
        assertThat(up.get("code").asInt()).as("上传 msg=" + text(up, "msg")).isZero();
        String url = up.get("data").get("url").asText();
        String thumb = up.get("data").get("thumbnailUrl").asText();
        // 随机 UUID 文件名（不可按时间枚举）+ 短时签名
        assertThat(url).matches("^/gudu/uploads/original/\\d{8}/[0-9a-f]{32}\\.png\\?e=\\d+&s=[A-Za-z0-9_\\-]+$");

        String served = url.replaceFirst("^/gudu", "");
        String servedThumb = thumb.replaceFirst("^/gudu", "");
        assertThat(http.getForEntity(CTX + served, byte[].class).getStatusCode().value()).as("带签名可读").isEqualTo(200);
        assertThat(http.getForEntity(CTX + servedThumb, byte[].class).getStatusCode().value()).isEqualTo(200);
        // 前端把 /original/ 换成 /thumbnail/ 取缩略图：签名同样有效
        assertThat(http.getForEntity(CTX + served.replace("/original/", "/thumbnail/"), byte[].class)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity(CTX + unsigned(served), byte[].class).getStatusCode().value())
                .as("未签名 403").isEqualTo(403);
        assertThat(http.getForEntity(CTX + served.replaceFirst("s=", "s=x"), byte[].class).getStatusCode().value())
                .as("篡改签名 403").isEqualTo(403);
        assertThat(http.getForEntity(CTX + served.replaceFirst("e=\\d+", "e=4102444800"), byte[].class)
                .getStatusCode().value()).as("篡改过期时间 403").isEqualTo(403);

        // 存量时间戳文件：同样需要签名（写在本机测试进程自己的临时上传目录，不是生产服务器目录）
        java.nio.file.Path legacyDir = java.nio.file.Paths.get(uploadDir, "original", "20260101");
        java.nio.file.Path legacyFile = legacyDir.resolve("1767225600123.png");
        String legacy = "/uploads/original/20260101/1767225600123.png";
        String oldAvatar = text(get(token, "/member/me").get("data"), "avatarUrl");
        try {
            try {
                java.nio.file.Files.createDirectories(legacyDir);
                java.nio.file.Files.write(legacyFile, png);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            assertThat(http.getForEntity(CTX + legacy, byte[].class).getStatusCode().value()).isEqualTo(403);
            assertThat(http.getForEntity(CTX + uploadSigner.sign(legacy), byte[].class).getStatusCode().value())
                    .isEqualTo(200);

            // 入参：服务端签发的地址可保存（入库为规范地址）；猜出来的未签名地址拒绝
            assertThat(put(token, "/member/me", Map.of("avatarUrl", url)).get("code").asInt()).isZero();
            String back = text(get(token, "/member/me").get("data"), "avatarUrl");
            assertThat(back).contains("?e=");
            assertThat(unsigned(back)).isEqualTo(unsigned(url));
            JsonNode guessed = put(token, "/member/me", Map.of("avatarUrl", "/gudu" + legacy));
            assertThat(guessed.get("code").asInt()).as("未签名地址应拒绝").isNotZero();
            assertThat(text(guessed, "msg")).contains("图片地址已失效");
        } finally {
            put(token, "/member/me", Map.of("avatarUrl", oldAvatar));
            try {
                java.nio.file.Files.deleteIfExists(legacyFile);
            } catch (java.io.IOException ignore) {
                // 本机临时目录，忽略
            }
        }
    }

    // ===================== P. 余量保质期 =====================

    @Test
    void P_保质期_设档带保质期_临期查询_清除_保质天数建议_入库带保质期() {
        String token = loginAdmin();
        LocalDate in2 = LocalDate.now().plusDays(2);

        // 设档 + 保质期 → 临期（3 天内）能查到，daysLeft=2
        JsonNode set = put(token, "/pantry/" + tomatoId + "/level",
                Map.of("level", "ENOUGH", "bestBefore", in2.toString(), "note", P + "保质期"));
        assertThat(set.get("code").asInt()).as("设档 msg=" + text(set, "msg")).isEqualTo(0);
        JsonNode exp = get(token, "/pantry/expiring?days=3");
        assertThat(exp.get("code").asInt()).isEqualTo(0);
        JsonNode tomato = byId(exp.get("data"), "ingredientId", tomatoId);
        assertThat(tomato).as("临期应含番茄").isNotNull();
        assertThat(tomato.get("daysLeft").asInt()).isEqualTo(2);
        assertThat(tomato.get("bestBefore").asText()).isEqualTo(in2.toString());
        assertThat(tomato.get("level").asText()).isEqualTo("ENOUGH");
        assertThat(tomato.get("ingredientName").asText()).isEqualTo("番茄");

        // grouped / item 带 bestBefore + daysLeft
        JsonNode grouped = get(token, "/pantry/grouped");
        JsonNode g = byId(grouped.get("data").get("items"), "ingredientId", tomatoId);
        assertThat(g).isNotNull();
        assertThat(g.get("daysLeft").asInt()).isEqualTo(2);
        assertThat(get(token, "/pantry/item?ingredientId=" + tomatoId).get("data").get("bestBefore").asText())
                .isEqualTo(in2.toString());

        // 传 null = 清除
        Map<String, Object> clear = new HashMap<>();
        clear.put("level", "ENOUGH");
        clear.put("bestBefore", null);
        assertThat(put(token, "/pantry/" + tomatoId + "/level", clear).get("code").asInt()).isEqualTo(0);
        JsonNode item = get(token, "/pantry/item?ingredientId=" + tomatoId).get("data");
        assertThat(item.get("bestBefore").isNull()).as("bestBefore 传 null 应清除").isTrue();
        assertThat(item.get("daysLeft").isNull()).isTrue();

        // 保质天数建议：番茄=蔬菜（默认 5 天）
        JsonNode sl = get(token, "/pantry/shelf-life?ingredientId=" + tomatoId);
        assertThat(sl.get("code").asInt()).isEqualTo(0);
        assertThat(sl.get("data").get("suggestedDays").asInt()).isPositive();

        // 手动入库带 bestBefore（鸡蛋）
        LocalDate in5 = LocalDate.now().plusDays(5);
        JsonNode manual = post(token, "/pantry/manual",
                Map.of("ingredientId", eggId, "level", "ENOUGH", "bestBefore", in5.toString(), "sourceNote", P + "入库"));
        assertThat(manual.get("code").asInt()).as("入库 msg=" + text(manual, "msg")).isEqualTo(0);
        assertThat(get(token, "/pantry/item?ingredientId=" + eggId).get("data").get("bestBefore").asText())
                .isEqualTo(in5.toString());
    }

    // ===================== Q. 菜谱覆盖率 =====================

    @Test
    void Q_菜谱搜索_登录带覆盖率_sort_coverage家里够的优先() {
        String token = loginAdmin();
        // 前置：番茄 / 鸡蛋都 ENOUGH → 自建番茄炒蛋 2/2
        setLevel(token, tomatoId, "ENOUGH", null);
        setLevel(token, eggId, "ENOUGH", null);
        String kw = P + "番茄炒蛋";
        JsonNode r = get(token, "/dish/search?keyword=" + kw + "&pageNum=1&pageSize=10");
        assertThat(r.get("code").asInt()).as("search msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode rec = byId(r.get("data").get("records"), "id", dishId);
        assertThat(rec).as("应搜到自建番茄炒蛋").isNotNull();
        JsonNode cov = rec.get("coverage");
        assertThat(cov.isNull()).as("登录应带 coverage").isFalse();
        assertThat(cov.get("total").asInt()).isEqualTo(2);
        assertThat(cov.get("enough").asInt()).isEqualTo(2);
        assertThat(cov.get("missing").isArray()).isTrue();

        // 番茄用完 → 1/2，missing 含番茄
        setLevel(token, tomatoId, "NONE", null);
        JsonNode r2 = get(token, "/dish/search?keyword=" + kw + "&pageNum=1&pageSize=10");
        JsonNode cov2 = byId(r2.get("data").get("records"), "id", dishId).get("coverage");
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

    // ===================== R. 常用食材 / 最近做过 / 单菜弹窗 =====================

    @Test
    void R_常用食材_单菜弹窗_最近做过() {
        String token = loginAdmin();
        setLevel(token, tomatoId, "ENOUGH", null);
        setLevel(token, eggId, "ENOUGH", null);

        JsonNode freq = get(token, "/ingredient/frequent?limit=8");
        assertThat(freq.get("code").asInt()).as("frequent msg=" + text(freq, "msg")).isEqualTo(0);
        assertThat(freq.get("data").size()).isBetween(1, 8);
        assertThat(freq.get("data").get(0).has("id")).isTrue();
        assertThat(freq.get("data").get(0).has("name")).isTrue();

        JsonNode mats = get(token, "/cook/dish/" + dishId + "/materials?servings=2");
        assertThat(mats.get("code").asInt()).as("materials msg=" + text(mats, "msg")).isEqualTo(0);
        assertThat(mats.get("data").get("menuId").isNull()).isTrue();
        assertThat(mats.get("data").get("items").size()).isEqualTo(2);
        assertThat(mats.get("data").get("items").get(0).get("usageTexts").get(0).asText()).contains("×2");

        assertThat(get(token, "/cook/dish/999999999/materials").get("code").asInt()).isNotEqualTo(0);

        JsonNode cook = post(token, "/cook/dish/" + dishId, Map.of("servings", 1));
        assertThat(cook.get("code").asInt()).as("cook msg=" + text(cook, "msg")).isEqualTo(0);
        JsonNode recent = get(token, "/cookbook/recent?limit=5");
        assertThat(recent.get("code").asInt()).isEqualTo(0);
        assertThat(recent.get("data").get(0).get("dishId").asLong()).isEqualTo(dishId);
        assertThat(recent.get("data").get(0).get("cookedCount").asInt()).isPositive();
        assertThat(recent.get("data").get(0).get("lastCookedAt").asText()).contains("T");
    }

    // ===================== S. 首页智荐 =====================

    /**
     * 与旧用例的差异：旧用例断言「首推菜带封面、且不是种子无封面菜」，依赖预发菜谱库；生产公共菜谱实际内容不可控，
     * 这里改为：自己录入明天到期的番茄库存 → 断言临期条目含番茄、各数组结构与上限正确、首推菜卡结构正确，不断言具体推荐了哪道菜。
     */
    @Test
    void S_首页智荐_临期番茄_主推番茄炒蛋() {
        String token = loginAdmin();
        setLevel(token, tomatoId, "ENOUGH", LocalDate.now().plusDays(1));
        JsonNode home = get(token, "/recommend/home");
        assertThat(home.get("code").asInt()).as("home msg=" + text(home, "msg")).isEqualTo(0);
        JsonNode d = home.get("data");
        for (String k : List.of("expiring", "expiringPicks", "frequent", "seasonal")) {
            assertThat(d.get(k).isArray()).as(k + " 应为数组").isTrue();
        }
        assertThat(byId(d.get("expiring"), "ingredientId", tomatoId)).as("临期应含自建的番茄库存").isNotNull();
        assertThat(d.get("expiring").size()).isLessThanOrEqualTo(5);
        // 自建菜「番茄炒蛋」用到番茄，候选池至少有它 → 首推 1~3 道
        assertThat(d.get("expiringPicks").size()).isBetween(1, 3);
        for (JsonNode c : d.get("expiringPicks")) {
            assertThat(c.has("coverage")).isTrue();
            assertThat(c.has("cookedCount")).isTrue();
            assertThat(c.get("dishId").asLong()).isPositive();
        }
        assertThat(d.get("frequent").size()).isLessThanOrEqualTo(6);
        assertThat(d.get("seasonal").size()).isLessThanOrEqualTo(6);
    }

    // ===================== T. 个人资料 =====================

    @Test
    void T_个人资料_读改_头像只收本站地址() {
        String token = loginAdmin();
        JsonNode me = get(token, "/member/me");
        assertThat(me.get("code").asInt()).as("me msg=" + text(me, "msg")).isEqualTo(0);
        String oldName = me.get("data").get("name").asText();
        // /member/me 的 isAdmin 读成员档案上的旧列 member.is_admin（仅展示），生产夹具只提了 account.platform_role，
        // 所以只断言字段存在；超管身份以登录响应（读 account.platform_role）为准
        assertThat(me.get("data").has("isAdmin")).isTrue();
        JsonNode login = post(null, "/auth/login", Map.of("username", adminPhone, "password", PW));
        assertThat(login.get("data").get("isAdmin").asBoolean()).as("登录响应里的超管标记").isTrue();
        assertThat(me.get("data").has("phone")).isTrue();

        try {
            JsonNode up = put(token, "/member/me", Map.of("name", P + "改名测试",
                    "avatarUrl", uploadSigner.sign("/gudu/uploads/e2e/a.jpg")));
            assertThat(up.get("code").asInt()).as("put me msg=" + text(up, "msg")).isEqualTo(0);
            JsonNode me2 = get(token, "/member/me").get("data");
            assertThat(me2.get("name").asText()).isEqualTo(P + "改名测试");
            assertThat(me2.get("avatarUrl").asText()).as("出参带签名").contains("?e=");
            assertThat(unsigned(me2.get("avatarUrl").asText())).isEqualTo("/gudu/uploads/e2e/a.jpg");

            assertThat(put(token, "/member/me", Map.of("avatarUrl", "https://evil.com/a.jpg")).get("code").asInt())
                    .as("外链头像应拒绝").isNotEqualTo(0);
            assertThat(put(token, "/member/me", Map.of("name", "x".repeat(21))).get("code").asInt())
                    .as("超长昵称应拒绝").isNotEqualTo(0);
        } finally {
            put(token, "/member/me", Map.of("name", oldName, "avatarUrl", ""));
        }
        assertThat(get(token, "/member/me").get("data").get("name").asText()).isEqualTo(oldName);
    }

    // ===================== U. 意见反馈 =====================

    /** 反馈每账号每天限 10 条：提交人用自建新账号，处理（列表 / 改状态 / 回复）用超管。 */
    @Test
    void U_意见反馈_提交_超管列表_改状态() {
        String userPhone = phoneFor("fb1");
        createAccount(userPhone, P + "反馈人1");
        String user = loginAs(userPhone, PW);
        String operator = loginAdmin();

        JsonNode bad = post(user, "/feedback", Map.of("content", "短"));
        assertThat(bad.get("code").asInt()).as("内容过短应拒绝").isNotEqualTo(0);

        JsonNode created = post(user, "/feedback", Map.of("content", P + "反馈：希望增加菜谱导出",
                "contact", "e2e@example.com", "images", List.of(uploadSigner.sign("/gudu/uploads/e2e/f.jpg")), "clientInfo", "e2e"));
        assertThat(created.get("code").asInt()).as("feedback msg=" + text(created, "msg")).isEqualTo(0);
        long id = created.get("data").asLong();

        JsonNode mine = findFeedback(operator, "status=NEW", id);
        assertThat(mine).as("NEW 列表应含刚提交的反馈").isNotNull();
        assertThat(unsigned(mine.get("images").get(0).asText())).isEqualTo("/gudu/uploads/e2e/f.jpg");
        assertThat(mine.get("memberName").isNull()).isFalse();

        assertThat(put(operator, "/feedback/" + id + "/status", Map.of("status", "DONE")).get("code").asInt()).isEqualTo(0);
        assertThat(findFeedback(operator, "status=DONE", id)).isNotNull();
    }

    @Test
    void U2_意见反馈闭环_类型来源_回复发站内消息_我的反馈可见() {
        String userPhone = phoneFor("fb2");
        createAccount(userPhone, P + "反馈人2");
        String user = loginAs(userPhone, PW);
        String operator = loginAdmin();

        assertThat(post(user, "/feedback", Map.of("content", P + "反馈：类型非法", "type", "bug"))
                .get("code").asInt()).as("非法类型应拒绝").isNotEqualTo(0);

        JsonNode created = post(user, "/feedback", Map.of("content", P + "反馈：AI 回答的做法不对",
                "type", "ai", "sourceRef", "agent:E2E-m1", "clientInfo", "e2e"));
        assertThat(created.get("code").asInt()).as("feedback msg=" + text(created, "msg")).isEqualTo(0);
        long id = created.get("data").asLong();

        // 超管按类型 + 状态筛选
        JsonNode row = findFeedback(operator, "type=ai&status=NEW", id);
        assertThat(row).as("ai+NEW 筛选应含刚提交的反馈").isNotNull();
        assertThat(row.get("type").asText()).isEqualTo("ai");
        assertThat(row.get("sourceRef").asText()).isEqualTo("agent:E2E-m1");
        for (JsonNode f : get(operator, "/feedback?type=complaint&pageSize=100").get("data").get("records")) {
            assertThat(f.get("id").asLong()).isNotEqualTo(id);
        }

        // 回复 → REPLIED + 提交人收到站内消息（ref 指向反馈）
        assertThat(put(operator, "/feedback/" + id + "/reply", Map.of("reply", " ")).get("code").asInt())
                .as("空回复应拒绝").isNotEqualTo(0);
        assertThat(put(operator, "/feedback/" + id + "/reply", Map.of("reply", P + "回复：已修正菜谱")).get("code").asInt())
                .isEqualTo(0);
        JsonNode detail = get(user, "/feedback/mine/" + id).get("data");
        assertThat(detail.get("status").asText()).isEqualTo("REPLIED");
        assertThat(detail.get("reply").asText()).isEqualTo(P + "回复：已修正菜谱");
        assertThat(detail.get("repliedAt").isNull()).isFalse();
        assertThat(byId(get(user, "/feedback/mine").get("data").get("records"), "id", id))
                .as("我的反馈列表应含该条").isNotNull();
        JsonNode notice = null;
        for (JsonNode n : get(user, "/notification").get("data")) {
            if ("feedback_reply".equals(text(n, "type")) && String.valueOf(id).equals(text(n, "refId"))) notice = n;
        }
        assertThat(notice).as("回复后应有 feedback_reply 站内消息").isNotNull();
        assertThat(text(notice, "content")).contains("已修正菜谱");

        // 回复并标记处理 → DONE
        assertThat(put(operator, "/feedback/" + id + "/reply", Map.of("reply", P + "回复：补充说明", "markDone", true))
                .get("code").asInt()).isEqualTo(0);
        assertThat(get(user, "/feedback/mine/" + id).get("data").get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void U3_个性化推荐开关_默认开_关闭后智荐与首屏只给通用推荐() {
        String userPhone = phoneFor("pref");
        createAccount(userPhone, P + "推荐开关");
        String token = loginAs(userPhone, PW);
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

    // ===================== V. 运营仪表盘 / 系统健康摘要（全站只读汇总，只断言结构） =====================

    @Test
    void V_运营仪表盘_超管统计() {
        String token = loginAdmin();
        JsonNode r = get(token, "/ops/stats?days=7");
        assertThat(r.get("code").asInt()).as("ops msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode d = r.get("data");
        for (String k : List.of("members", "accounts", "dishes")) {
            assertThat(d.get("totals").get(k).asLong()).as("totals." + k).isGreaterThanOrEqualTo(0);
        }
        assertThat(d.get("totals").has("feedbackOpen")).isTrue();
        assertThat(d.get("last7d").has("aiCostYuan")).isTrue();
        assertThat(d.get("trend").size()).isEqualTo(7);
        assertThat(d.get("trend").get(6).get("date").asText()).hasSize(10);
    }

    /** 只读探测（应用 / MySQL / Redis / 向量库 / AI），不触发语义检索重建。 */
    @Test
    void V_系统健康摘要_超管可读_含五项探测() {
        String token = loginAdmin();
        JsonNode r = get(token, "/ops/health");
        assertThat(r.get("code").asInt()).as("health msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode items = r.get("data").get("items");
        assertThat(items.size()).isEqualTo(5);
        for (JsonNode it : items) {
            assertThat(it.get("status").asText()).isIn("ok", "err");
            assertThat(it.has("latencyMs")).isTrue();
        }
    }

    // ===================== W. 原型改版 P0 =====================

    @Test
    void W1_食材插画key_各接口透出() {
        String token = loginAdmin();
        // 前置：番茄明天到期、鸡蛋充足
        setLevel(token, tomatoId, "ENOUGH", LocalDate.now().plusDays(1));
        setLevel(token, eggId, "ENOUGH", null);

        // 食材库
        JsonNode ings = get(token, "/ingredient?keyword=番茄&pageNum=1&pageSize=50");
        assertThat(ings.get("code").asInt()).as("ingredient msg=" + text(ings, "msg")).isEqualTo(0);
        assertThat(byId(ings.get("data").get("records"), "id", tomatoId).get("iconKey").asText()).isEqualTo("tomato");
        // 菜谱详情用料
        JsonNode detail = get(token, "/dish/" + dishId).get("data");
        JsonNode dishIngs = detail.has("ingredients") ? detail.get("ingredients") : detail.get("dish").get("ingredients");
        assertThat(byId(dishIngs, "ingredientId", tomatoId).get("iconKey").asText()).isEqualTo("tomato");
        assertThat(byId(dishIngs, "ingredientId", eggId).get("iconKey").asText()).isEqualTo("egg");
        // 余量分组 / 详情 / 临期
        assertThat(byId(get(token, "/pantry/grouped").get("data").get("items"), "ingredientId", tomatoId)
                .get("iconKey").asText()).isEqualTo("tomato");
        assertThat(get(token, "/pantry/item?ingredientId=" + tomatoId).get("data").get("iconKey").asText()).isEqualTo("tomato");
        assertThat(byId(get(token, "/pantry/expiring?days=3").get("data"), "ingredientId", tomatoId)
                .get("iconKey").asText()).isEqualTo("tomato");
        // 做菜确认弹窗
        JsonNode mats = get(token, "/cook/dish/" + dishId + "/materials").get("data").get("items");
        assertThat(byId(mats, "ingredientId", tomatoId).get("iconKey").asText()).isEqualTo("tomato");
        // 采购：关联食材取库里 key；手动项按名称推断（抽纸→tissue）
        long listId = post(token, "/shopping/create", null).get("data").asLong();
        long newId = -1;
        try {
            post(token, "/shopping/item/custom", Map.of("listId", listId, "name", "番茄", "amount", 1));
            post(token, "/shopping/item/custom", Map.of("listId", listId, "name", P + "抽纸"));
            Map<String, String> iconByName = new HashMap<>();
            for (JsonNode it : get(token, "/shopping/" + listId).get("data").get("items")) {
                iconByName.put(it.get("ingredientName").asText(), it.get("iconKey").isNull() ? null : it.get("iconKey").asText());
            }
            assertThat(iconByName).containsEntry("番茄", "tomato").containsEntry(P + "抽纸", "tissue");

            // 新建食材自动算 key
            Map<String, Object> ing = new HashMap<>();
            ing.put("name", P + "胡萝卜");
            ing.put("edible", 1);
            JsonNode created = post(token, "/ingredient", Map.of("ingredient", ing, "nutritions", List.of()));
            assertThat(created.get("code").asInt()).as("新建食材 msg=" + text(created, "msg")).isEqualTo(0);
            newId = created.get("data").asLong();
            assertThat(get(token, "/ingredient/" + newId).get("data").get("iconKey").asText()).isEqualTo("carrot");
        } finally {
            delete(token, "/shopping/" + listId);
            if (newId > 0) delete(token, "/ingredient/" + newId);
        }
    }

    @Test
    void W2_余量统计_我的统计_最近做菜人() {
        String token = loginAdmin();
        // 前置：番茄明天到期、鸡蛋充足 → 余量统计 enough>=2、expiring3d>=1
        setLevel(token, tomatoId, "ENOUGH", LocalDate.now().plusDays(1));
        setLevel(token, eggId, "ENOUGH", null);

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
        JsonNode cook = post(token, "/cook/dish/" + dishId, Map.of("servings", 1, "usedUp", List.of(tomatoId)));
        assertThat(cook.get("code").asInt()).as("cook msg=" + text(cook, "msg")).isEqualTo(0);
        JsonNode after = get(token, "/me/stats").get("data");
        assertThat(after.get("cookedThisMonth").asLong()).isEqualTo(before.get("data").get("cookedThisMonth").asLong() + 1);
        assertThat(after.get("savedFromWaste").asLong()).isEqualTo(before.get("data").get("savedFromWaste").asLong() + 1);
        // 家庭菜谱数只统计本家私有菜谱：夹具里的自建「番茄炒蛋」就是一道
        assertThat(after.get("familyDishes").asLong()).isPositive();

        // 菜谱列表：最近一次做菜时间 + 做菜人
        JsonNode rec = byId(get(token, "/dish/search?keyword=" + P + "番茄炒蛋&pageNum=1&pageSize=10").get("data").get("records"),
                "id", dishId);
        assertThat(rec).isNotNull();
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
}
