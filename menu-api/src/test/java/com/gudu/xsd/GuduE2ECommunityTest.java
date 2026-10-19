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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 厨房圈 E2E（showcase-ai-community S3，契约 contracts.md §2）。
 *
 * <p>自建 E2E 成员（phone 1390000710x）与 'E2E' 前缀帖子，e2e-seed.sql 的 [community] 区块兜底清理；
 * 「照着做」复制出的菜在用例末尾删除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:e2e-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:e2e-teardown.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class GuduE2ECommunityTest {

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper om = new ObjectMapper();

    private static final long DISH_FANQIE = 1L;   // 种子菜
    private static final long TOPIC_CHALLENGE = 1L; // V71 种子：#一周不重样
    private static final String IMG = "/gudu/uploads/e2e/community-a.jpg";

    // ===================== HTTP helper（同 GuduE2EFlowTest 范式） =====================

    private String login(String username, String password) {
        JsonNode r = post(null, "/auth/login", Map.of("username", username, "password", password));
        assertThat(r.get("code").asInt()).as("登录 " + username + " msg=" + text(r, "msg")).isEqualTo(0);
        return r.get("data").get("token").asText();
    }

    private String newUser(String admin, String name, String phone) {
        JsonNode created = post(admin, "/member", Map.of("name", name, "phone", phone, "password", "e2etest123"));
        assertThat(created.get("code").asInt()).as("建成员 msg=" + text(created, "msg")).isEqualTo(0);
        return login(phone, "e2etest123");
    }

    private JsonNode post(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.POST, body);
    }

    private JsonNode get(String token, String path) {
        return exchange(token, path, HttpMethod.GET, null);
    }

    private JsonNode delete(String token, String path) {
        return exchange(token, path, HttpMethod.DELETE, null);
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

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    private static JsonNode ok(JsonNode r, String what) {
        assertThat(r.get("code").asInt()).as(what + " msg=" + text(r, "msg")).isEqualTo(0);
        return r.get("data");
    }

    private long publish(String token, String title, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("text", "E2E 正文：火候是关键");
        body.put("imageUrls", List.of(IMG));
        body.put("dishId", DISH_FANQIE);
        body.put("topicIds", List.of(TOPIC_CHALLENGE));
        body.put("coverRatio", 0.75);
        body.putAll(extra);
        JsonNode d = ok(post(token, "/community/posts", body), "发布 " + title);
        return d.get("postId").asLong();
    }

    private List<Long> ids(JsonNode page) {
        List<Long> out = new ArrayList<>();
        page.get("items").forEach(i -> out.add(i.get("postId").asLong()));
        return out;
    }

    // ===================== A. 发布 → Feed → 互动 → 照着做 → 评论 → 话题 =====================

    @Test
    void A_发布与互动主链路() {
        String admin = login("admin", "admin123");
        String bob = newUser(admin, "E2E小厨", "13900007101");

        long p1 = publish(bob, "E2E第一次做番茄炒蛋", Map.of());
        long p2 = publish(bob, "E2E周五打卡", Map.of());
        JsonNode d1 = ok(get(bob, "/community/posts/" + p1), "作者看详情");
        assertThat(d1.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(d1.get("coverRatio").asDouble()).isEqualTo(0.75);
        assertThat(d1.get("topics").get(0).get("name").asText()).isEqualTo("一周不重样");
        assertThat(d1.get("recipe").get("id").asLong()).isEqualTo(DISH_FANQIE);

        // 推荐流：size=1 逐页翻，两帖都出现且不重复
        JsonNode page1 = ok(get(admin, "/community/feed?tab=recommend&size=1"), "推荐第一页");
        assertThat(page1.get("nextCursor").isNull()).isFalse();
        Set<Long> seen = new HashSet<>(ids(page1));
        JsonNode page = page1;
        for (int i = 0; i < 50 && !page.get("nextCursor").isNull() && !(seen.contains(p1) && seen.contains(p2)); i++) {
            page = ok(get(admin, "/community/feed?tab=recommend&size=1&cursor=" + page.get("nextCursor").asText()), "翻页");
            for (Long id : ids(page)) assertThat(seen.add(id)).as("翻页不重复 " + id).isTrue();
        }
        assertThat(seen).contains(p1, p2);

        // 点赞幂等：重复点只算一次；取消回到 0
        ok(post(admin, "/community/posts/" + p1 + "/reactions", Map.of("type", "LIKE", "on", true)), "点赞");
        JsonNode like2 = ok(post(admin, "/community/posts/" + p1 + "/reactions", Map.of("type", "LIKE", "on", true)), "重复点赞");
        assertThat(like2.get("likeCount").asLong()).isEqualTo(1);
        assertThat(like2.get("liked").asBoolean()).isTrue();
        JsonNode col = ok(post(admin, "/community/posts/" + p1 + "/reactions", Map.of("type", "COLLECT", "on", true)), "收藏");
        assertThat(col.get("collectCount").asLong()).isEqualTo(1);
        JsonNode unlike = ok(post(admin, "/community/posts/" + p1 + "/reactions", Map.of("type", "LIKE", "on", false)), "取消赞");
        assertThat(unlike.get("likeCount").asLong()).isZero();

        // 关注 → 关注流有作者的帖；横幅出现关注的人
        JsonNode bobInfo = d1.get("author");
        long bobId = bobInfo.get("userId").asLong();
        assertThat(ok(post(admin, "/community/users/" + bobId + "/follow", Map.of("on", true)), "关注")
                .get("following").asBoolean()).isTrue();
        assertThat(ids(ok(get(admin, "/community/feed?tab=follow"), "关注流"))).contains(p1, p2);
        JsonNode banner = ok(get(admin, "/community/banner"), "横幅");
        assertThat(banner.get("topic").get("name").asText()).isEqualTo("一周不重样");
        assertThat(banner.get("topic").get("startDate").asText()).as("挑战周期从周一开始").isNotBlank();
        assertThat(banner.get("followingUpdates").toString()).contains("E2E小厨");
        assertThat(ok(get(admin, "/community/posts/" + p1), "详情").get("following").asBoolean()).isTrue();

        // 照着做：复制到我家，幂等；作者收到通知
        long copied = ok(post(admin, "/community/posts/" + p1 + "/copy-recipe", null), "照着做").get("dishId").asLong();
        try {
            assertThat(copied).isNotEqualTo(DISH_FANQIE);
            assertThat(ok(post(admin, "/community/posts/" + p1 + "/copy-recipe", null), "再点照着做")
                    .get("dishId").asLong()).isEqualTo(copied);
            assertThat(ok(get(admin, "/community/posts/" + p1), "详情").get("copyCount").asLong()).isEqualTo(1);
            assertThat(ok(get(admin, "/community/posts/" + p1 + "/copies"), "他们照着做了").get("items").size()).isEqualTo(1);
            assertThat(ok(get(admin, "/dish/" + copied), "复制出的菜").toString()).contains("厨房圈");
        } finally {
            delete(admin, "/dish/" + copied);
        }

        // 评论两级：作者回复带 isAuthor
        long c1 = ok(post(admin, "/community/posts/" + p1 + "/comments", Map.of("text", "看着就好吃")), "评论")
                .get("commentId").asLong();
        ok(post(bob, "/community/posts/" + p1 + "/comments", Map.of("text", "谢谢！", "parentId", c1)), "回复");
        JsonNode comments = ok(get(bob, "/community/posts/" + p1 + "/comments"), "评论列表");
        assertThat(comments.get("items").get(0).get("replies").get(0).get("isAuthor").asBoolean()).isTrue();
        assertThat(ok(get(admin, "/community/posts/" + p1), "详情").get("commentCount").asLong()).isEqualTo(2);

        // 话题挑战：今天已打卡，7 天周一开始；话题流
        JsonNode topic = ok(get(bob, "/community/topics/" + TOPIC_CHALLENGE), "话题");
        JsonNode days = topic.get("myProgress").get("days");
        assertThat(days.size()).isEqualTo(7);
        assertThat(java.time.LocalDate.parse(days.get(0).get("date").asText()).getDayOfWeek())
                .isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(topic.get("myProgress").get("doneCount").asInt()).isEqualTo(1);
        assertThat(topic.get("myRank").isNull()).isFalse();
        assertThat(ids(ok(get(admin, "/community/feed?tab=topic&topicId=" + TOPIC_CHALLENGE), "话题流"))).contains(p1, p2);

        // 话题列表（发布页选话题）
        JsonNode topicList = ok(get(bob, "/community/topics?status=ALL&size=10"), "话题列表");
        assertThat(topicList.toString()).contains("一周不重样").contains("宝宝辅食");
        assertThat(ok(get(bob, "/community/topics"), "进行中话题").size()).isPositive();

        // AI 帮写（模板）
        assertThat(ok(post(bob, "/community/drafts/ai-text", Map.of("dishId", DISH_FANQIE, "style", "short")), "AI 帮写")
                .get("text").asText()).isNotBlank();

        // 删除：他人看不到
        ok(delete(bob, "/community/posts/" + p2), "删帖");
        assertThat(get(admin, "/community/posts/" + p2).get("code").asInt()).isNotEqualTo(0);
    }

    // ===================== B. 治理：高风险 → 审核 → 隐藏 → 申诉；举报阈值 =====================

    @Test
    void B_内容治理与申诉() {
        String admin = login("admin", "admin123");
        String bob = newUser(admin, "E2E小厨", "13900007101");
        String carol = newUser(admin, "E2E甲", "13900007102");
        String dave = newUser(admin, "E2E乙", "13900007103");

        // 高风险：他人不可见，进审核队列，带 AI 分项与摘要
        long risky = publish(bob, "E2E加微信 abcdef12 私聊", Map.of());
        assertThat(ok(get(bob, "/community/posts/" + risky), "作者看审核中").get("status").asText()).isEqualTo("REVIEWING");
        assertThat(get(carol, "/community/posts/" + risky).get("code").asInt()).as("他人不可见").isNotEqualTo(0);
        JsonNode queue = ok(get(admin, "/admin/community/queue?status=PENDING&risk=HIGH&size=50"), "审核队列");
        JsonNode item = null;
        for (JsonNode i : queue.get("items")) if (i.get("postId").asLong() == risky) item = i;
        assertThat(item).as("高风险帖在队列").isNotNull();
        assertThat(item.get("aiScores").size()).isPositive();
        assertThat(item.get("aiSummary").asText()).contains("高风险");
        assertThat(item.get("authorHistory").get("posts").asLong()).isPositive();

        // 站外图片被拦截：管理员详情能看到 blockedImages
        long ext = publish(bob, "E2E带外链图", Map.of("imageUrls", List.of(IMG, "https://evil.example.com/x.png")));
        JsonNode extDetail = ok(get(admin, "/admin/community/posts/" + ext), "管理员详情");
        assertThat(extDetail.get("blockedImages").get(0).asText()).isEqualTo("https://evil.example.com/x.png");
        assertThat(extDetail.get("images").size()).isEqualTo(1);
        assertThat(extDetail.get("status").asText()).isEqualTo("REVIEWING");

        // 隐藏 → 作者申诉（一次）→ 申诉队列 → 再处置后申诉关闭
        ok(post(admin, "/admin/community/posts/" + risky + "/moderate",
                Map.of("action", "HIDE", "reason", "联系方式导流", "note", "E2E")), "隐藏");
        JsonNode afterHide = ok(get(admin, "/admin/community/posts/" + risky), "处置后详情");
        assertThat(afterHide.get("moderation").get("action").asText()).isEqualTo("HIDE");
        assertThat(post(carol, "/community/posts/" + risky + "/appeal", Map.of("text", "不是我的")).get("code").asInt())
                .as("非作者不能申诉").isNotEqualTo(0);
        ok(post(bob, "/community/posts/" + risky + "/appeal", Map.of("text", "E2E 那是我家的菜谱号")), "申诉");
        assertThat(post(bob, "/community/posts/" + risky + "/appeal", Map.of("text", "再来")).get("code").asInt())
                .as("只能申诉一次").isNotEqualTo(0);
        JsonNode appealed = ok(get(admin, "/admin/community/queue?status=APPEALED&size=50"), "申诉队列");
        assertThat(appealed.toString()).contains("E2E 那是我家的菜谱号");
        assertThat(ok(get(admin, "/admin/community/stats"), "统计").get("appealed").asLong()).isPositive();
        ok(post(admin, "/admin/community/posts/" + risky + "/moderate",
                Map.of("action", "APPROVE", "reason", "申诉成立")), "申诉后通过");
        assertThat(ok(get(carol, "/community/posts/" + risky), "通过后他人可见").get("status").asText()).isEqualTo("PUBLISHED");
        JsonNode done = ok(get(admin, "/admin/community/queue?status=DONE&size=50"), "已处理队列");
        assertThat(ids(done)).contains(risky);

        // 举报阈值：3 个不同用户举报自动隐藏；重复举报不计
        long normal = publish(bob, "E2E正常帖", Map.of());
        ok(post(admin, "/community/reports", Map.of("postId", normal, "reason", "广告")), "举报1");
        ok(post(admin, "/community/reports", Map.of("postId", normal, "reason", "广告")), "重复举报");
        ok(post(carol, "/community/reports", Map.of("postId", normal, "reason", "广告")), "举报2");
        assertThat(get(dave, "/community/posts/" + normal).get("code").asInt()).as("两人举报仍可见").isZero();
        ok(post(dave, "/community/reports", Map.of("postId", normal, "reason", "广告")), "举报3");
        assertThat(get(dave, "/community/posts/" + normal).get("code").asInt()).as("三人举报自动隐藏").isNotEqualTo(0);
        JsonNode hidden = ok(get(admin, "/admin/community/posts/" + normal), "被举报帖管理员详情");
        assertThat(hidden.get("reportCount").asLong()).isEqualTo(3);
        assertThat(hidden.get("reports").size()).isEqualTo(3);

        JsonNode stats = ok(get(admin, "/admin/community/stats"), "统计");
        assertThat(stats.get("newPosts").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(stats.has("avgHandleMinutes")).isTrue();
        assertThat(stats.get("autoHidden").asLong()).isPositive();

        // 封禁 7 天：作者不能再发帖
        ok(post(admin, "/admin/community/posts/" + normal + "/moderate",
                Map.of("action", "BAN_7D", "reason", "多次违规")), "封禁");
        Map<String, Object> body = new HashMap<>();
        body.put("title", "E2E封禁后发帖");
        body.put("imageUrls", List.of(IMG));
        assertThat(post(bob, "/community/posts", body).get("code").asInt()).as("封禁期不能发帖").isNotEqualTo(0);
        assertThat(ok(get(admin, "/admin/community/posts/" + normal), "详情").get("authorHistory").get("bans").asLong())
                .isEqualTo(1);

        // 非管理员不能调后台
        assertThat(get(bob, "/admin/community/stats").get("code").asInt()).isNotEqualTo(0);
    }
}
