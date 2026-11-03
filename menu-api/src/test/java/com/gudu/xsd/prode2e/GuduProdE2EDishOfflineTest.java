package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公共菜谱下架 / 重新上架生产 E2E（V101）：运营后台身份自建 E2E_PROD_ 前缀公共菜谱 → 下架 →
 * 普通账号搜索不到、详情仍可读且带下架标记、不能复制到我家 → 重新上架 → 搜索得到。
 * 菜谱名 / 下架原因均带 E2E_PROD_ 前缀，由基类按前缀清理（菜谱、操作日志）；不动任何已有公共菜谱。
 */
class GuduProdE2EDishOfflineTest extends ProdE2EBase {

    private static JsonNode ok(JsonNode r, String what) {
        assertThat(r.get("code").asInt()).as(what + " msg=" + text(r, "msg")).isEqualTo(0);
        return r.get("data");
    }

    private static int total(JsonNode searchResp) {
        return searchResp.get("data").get("total").asInt();
    }

    @Test
    void 公共菜谱_下架后新入口隐藏详情仍可读_重新上架恢复() {
        String admin = loginAdminScope(); // 后台身份新建 => 公共菜谱
        String name = P + "下架测试菜";
        Map<String, Object> body = Map.of(
                "dish", Map.of("name", name, "difficulty", 1, "cookTime", 10, "prepTime", 5),
                "ingredients", List.of(Map.of("ingredientId", tomatoId, "amount", 200, "unitId", unitGId)),
                "steps", List.of(Map.of("seq", 1, "text", P + "步骤")),
                "cuisineIds", List.of(), "tagIds", List.of(), "categoryIds", List.of());
        long id = ok(post(admin, "/dish", body), "后台新建公共菜谱").asLong();
        assertThat(jdbc.queryForObject("SELECT family_id IS NULL FROM dish WHERE id = ?", Boolean.class, id))
                .as("后台身份建的是公共菜谱").isTrue();

        // 普通账号（自建家庭，非运营）
        JsonNode login = registerAndLogin(phoneFor("offline-user"), "下架用户");
        String user = login.get("token").asText();
        String kw = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);

        // 上架时：搜索得到、详情不带下架标记
        assertThat(total(get(user, "/dish/search?keyword=" + kw + "&pageNum=1&pageSize=10"))).isEqualTo(1);
        assertThat(ok(get(user, "/dish/" + id), "上架详情").get("dish").path("offline").asBoolean(false)).isFalse();

        // 下架：普通账号不能操作；原因必填；只允许公共菜谱
        assertThat(post(user, "/admin/dish-library/" + id + "/offline", Map.of("reason", P + "越权")).get("code").asInt())
                .as("普通账号不能下架").isNotZero();
        assertThat(post(admin, "/admin/dish-library/" + id + "/offline", Map.of("reason", " ")).get("code").asInt())
                .as("原因必填").isNotZero();
        long privateId = createDish(loginAdmin(), P + "下架测试私有菜", List.of(
                Map.of("ingredientId", tomatoId, "amount", 100, "unitId", unitGId)));
        assertThat(post(admin, "/admin/dish-library/" + privateId + "/offline", Map.of("reason", P + "私有")).get("code").asInt())
                .as("家庭私有菜谱不能下架").isNotZero();

        JsonNode off = ok(post(admin, "/admin/dish-library/" + id + "/offline", Map.of("reason", P + "下架原因")), "下架");
        assertThat(off.get("offline").asBoolean()).isTrue();
        assertThat(post(admin, "/admin/dish-library/" + id + "/offline", Map.of("reason", P + "重复")).get("code").asInt())
                .as("重复下架拒绝").isNotZero();

        // 后台公共菜谱库：按状态筛选
        JsonNode offList = ok(get(admin, "/admin/dish-library?status=offline&keyword=" + kw + "&pageNum=1&pageSize=10"), "已下架列表");
        assertThat(offList.get("total").asInt()).isEqualTo(1);
        assertThat(offList.get("records").get(0).get("offlineReason").asText()).isEqualTo(P + "下架原因");
        assertThat(ok(get(admin, "/admin/dish-library?status=online&keyword=" + kw + "&pageNum=1&pageSize=10"), "上架列表")
                .get("total").asInt()).isZero();

        // 操作日志留痕
        JsonNode logs = ok(get(admin, "/admin/operation-logs?targetType=DISH&targetId=" + id), "操作日志");
        assertThat(logs.get("records").get(0).get("action").asText()).isEqualTo("OFFLINE_DISH");

        // 新入口：普通账号搜索不到、不能复制到我家；已下架仍能读详情并带标记
        assertThat(total(get(user, "/dish/search?keyword=" + kw + "&pageNum=1&pageSize=10"))).isZero();
        assertThat(total(post(user, "/dish/search", Map.of("keyword", name, "pageNum", 1, "pageSize", 10)))).isZero();
        JsonNode detail = ok(get(user, "/dish/" + id), "下架后详情仍可读");
        assertThat(detail.get("dish").get("name").asText()).isEqualTo(name);
        assertThat(detail.get("dish").get("offline").asBoolean()).isTrue();
        assertThat(detail.get("dish").has("offlineReason")).as("下架原因不对用户端输出").isFalse();
        assertThat(post(user, "/dish/" + id + "/fork", Map.of()).get("code").asInt()).as("下架菜谱不能复制").isNotZero();

        // 重新上架：搜索恢复、详情不再带标记
        JsonNode on = ok(post(admin, "/admin/dish-library/" + id + "/online", Map.of("reason", P + "恢复")), "重新上架");
        assertThat(on.get("offline").asBoolean()).isFalse();
        assertThat(total(get(user, "/dish/search?keyword=" + kw + "&pageNum=1&pageSize=10"))).isEqualTo(1);
        assertThat(ok(get(user, "/dish/" + id), "上架后详情").get("dish").path("offline").asBoolean(false)).isFalse();
        assertThat(post(admin, "/admin/dish-library/" + id + "/online", Map.of()).get("code").asInt())
                .as("未下架不能重新上架").isNotZero();

        // 清理：菜谱本体（含已写的操作日志，按 reason 前缀）由基类 @AfterAll 清理，这里先删菜谱便于残留自检
        assertThat(delete(admin, "/dish/" + id).get("code").asInt()).isZero();
    }
}
