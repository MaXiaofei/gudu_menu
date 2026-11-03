package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudu.xsd.common.privacy.RetentionPurger;
import com.gudu.xsd.config.WxProperties;
import com.gudu.xsd.common.upload.UploadUrlSigner;
import com.gudu.xsd.modules.privacy.application.DataRetentionJob;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 生产端到端：内容安全 / 合规 / 紧急关停 / 隐私 / 后台风控（旧 GuduE2ECoverageTest 第三组：X、Y、Z、Z2、Z3、Q、R、S、Y1-Y4、Z_内容安全补全）。
 *
 * <p>生产安全约束：
 * <ul>
 *   <li>对话输入拦截只发会在「调模型之前」就被拦的消息（内容安全 GUARDED / 开关 5032 / 5033），绝不放行到模型；</li>
 *   <li>微信内容安全回调只构造 trace_id 以 E2E_PROD_ 开头的 content_media_check 自建记录，且只改本进程的 WxProperties 令牌；</li>
 *   <li>厨房圈开关 / 生成式 AI 开关 / AI 额度会改全站配置：先快照，关停窗口内只做必要请求，finally 先经接口按原值改回、再兜底还原校验；</li>
 *   <li>保存期限清理不调用 DataRetentionJob.runOnce（会删除生产真实超期数据），改为只读配置检查；</li>
 *   <li>封禁 / 注销只针对自建账号。</li>
 * </ul>
 */
class GuduProdE2ESafetyTest extends ProdE2EBase {

    @org.springframework.beans.factory.annotation.Autowired
    private UploadUrlSigner uploadSigner;


    @Autowired
    private WxProperties wxProps;
    @Autowired
    private DataRetentionJob retentionJob;
    @Autowired
    private List<RetentionPurger> purgers;
    @Autowired
    private Environment env;

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    // ------------------------------------------------------------------ 辅助

    /** 去掉上传地址签名，用于比较入库的规范地址。 */
    private static String unsigned(String url) {
        return UploadUrlSigner.strip(url);
    }

    private static boolean hasRecord(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) {
            if (value.equals(text(n, field))) return true;
        }
        return false;
    }

    /** 上传一张小图（usage=avatar），返回响应里的 url。 */
    private String uploadPng(String token, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(PNG) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("usage", "avatar");
        JsonNode up = post(token, "/file/upload", form);
        assertThat(up.get("code").asInt()).as("上传 msg=" + text(up, "msg")).isEqualTo(0);
        return up.get("data").get("url").asText();
    }

    /** 微信消息推送（明文模式）：返回响应体文本。 */
    private String push(String signature, String timestamp, String nonce, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(CTX + "/wx/push?signature=" + signature + "&timestamp=" + timestamp + "&nonce=" + nonce,
                HttpMethod.POST, new HttpEntity<>(body, h), String.class).getBody();
    }

    /** 以 SSE 发一条消息，返回原始事件流文本（只用于「必被拦截」的消息）。 */
    private String sendAgentMessage(String token, String sessionId, Map<String, Object> body, String client) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", token);
        if (client != null) h.set("X-Client", client);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return http.exchange(CTX + "/agent/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(om.writeValueAsString(body), h), String.class).getBody();
    }

    /** 发一条微信「图片检测结果」推送（自建 trace_id）。 */
    private void pushMediaVerdict(String traceId, String nonce, boolean checkBadSign) throws Exception {
        String oldToken = wxProps.getToken();
        wxProps.setToken("e2e-push-token");
        try {
            String ts = String.valueOf(java.time.Instant.now().getEpochSecond());
            String pushBody = "{\"ToUserName\":\"gh_e2e\",\"FromUserName\":\"o_e2e\",\"CreateTime\":" + ts
                    + ",\"MsgType\":\"event\",\"Event\":\"wxa_media_check\",\"appid\":\"\",\"trace_id\":\""
                    + traceId + "\",\"version\":2,\"detail\":[],\"errcode\":0,\"errmsg\":\"ok\","
                    + "\"result\":{\"suggest\":\"risky\",\"label\":20002}}";
            if (checkBadSign) {
                assertThat(push("bad-sign", ts, nonce, pushBody)).isEqualTo("invalid signature");
                assertThat(jdbc.queryForObject("SELECT status FROM content_media_check WHERE trace_id = ?", String.class,
                        traceId)).isEqualTo("PENDING");
            }
            String sign = com.gudu.xsd.modules.contentsecurity.domain.service.WxPushSignature.sign(
                    "e2e-push-token", ts, nonce);
            assertThat(push(sign, ts, nonce, pushBody)).isEqualTo("success");
            if (checkBadSign) {
                assertThat(push(sign, ts, nonce, pushBody)).as("重复推送幂等").isEqualTo("success");
            }
        } finally {
            wxProps.setToken(oldToken);
        }
    }

    private String newTraceId() {
        return P + UUID.randomUUID();
    }

    // ===================== X. 内容安全 =====================

    /**
     * prod-e2e profile 关闭微信真实调用（gudu.content-security.wx-enabled=false），超管为账号密码登录（无 openid）：
     * 文本走规则兜底；图片回调用签名推送模拟微信（自建 E2E_PROD_ 前缀 trace_id，只处理这些记录）。
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
        JsonNode fb = post(token, "/feedback", Map.of("content", P + "反馈：有人在评论区代开发票"));
        assertThat(fb.get("code").asInt()).as("反馈违规 msg=" + text(fb, "msg")).isEqualTo(451);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback WHERE content = ?", Integer.class,
                P + "反馈：有人在评论区代开发票")).as("违规反馈未落库").isZero();
        assertThat(post(token, "/feedback", Map.of("content", P + "反馈：希望增加菜谱导出")).get("code").asInt())
                .isEqualTo(0);

        // 3. 上传一张图，模拟已提交微信检测（trace_id E2E_PROD_ 前缀）
        String url = uploadPng(token, "e2e-prod-cs.png");
        String servedPath = url.replaceFirst("^/gudu", "");
        assertThat(http.getForEntity(CTX + servedPath, byte[].class).getStatusCode().value()).isEqualTo(200);
        String traceId = newTraceId();
        jdbc.update("INSERT INTO content_media_check(trace_id, media_url, member_id, purpose, status) "
                + "VALUES (?, ?, NULL, 'AVATAR', 'PENDING')", traceId, unsigned(url));

        // 3b. 检测结果回来前：图已被用作头像、发进厨房圈（待回调的图片放行）
        String oldAvatar = text(get(token, "/member/me").get("data"), "avatarUrl");
        assertThat(put(token, "/member/me", Map.of("avatarUrl", url)).get("code").asInt()).isZero();
        assertThat(unsigned(text(get(token, "/member/me").get("data"), "avatarUrl"))).isEqualTo(unsigned(url));
        Map<String, Object> postBody = new HashMap<>();
        postBody.put("title", P + "内容安全帖");
        postBody.put("text", P + "正文：今天的番茄炒蛋");
        postBody.put("imageUrls", List.of(url));
        postBody.put("coverRatio", 0.75);
        JsonNode published = post(token, "/community/posts", postBody);
        assertThat(published.get("code").asInt()).as("发帖 msg=" + text(published, "msg")).isZero();
        long postId = published.get("data").get("postId").asLong();

        // 4. 微信消息推送：签名不对拒绝；签名正确 + risky → 定论 RISKY、订阅方处置、文件隔离
        pushMediaVerdict(traceId, "n1", true);
        assertThat(jdbc.queryForObject("SELECT status FROM content_media_check WHERE trace_id = ?", String.class,
                traceId)).isEqualTo("RISKY");
        // 原地址换成占位图：仍 200（菜谱 / 食记等引用处不出现坏图），但已不是原图
        ResponseEntity<byte[]> served = http.getForEntity(CTX + servedPath, byte[].class);
        assertThat(served.getStatusCode().value()).as("原地址换占位图").isEqualTo(200);
        assertThat(served.getBody()).isNotEqualTo(PNG);
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(served.getBody())).getWidth())
                .as("占位图 600px").isEqualTo(600);

        // 头像订阅方：已清空（前端回退默认头像）
        assertThat(text(get(token, "/member/me").get("data"), "avatarUrl")).as("违规头像已清空").isEmpty();

        // 帖子订阅方：他人不可见；作者可见 HIDDEN + 「图片未通过审核」
        String viewer = registerAndLogin(phoneFor("x_viewer"), "围观者").get("token").asText();
        assertThat(get(viewer, "/community/posts/" + postId).get("code").asInt()).as("他人不可见").isNotZero();
        JsonNode mine = get(token, "/community/posts/" + postId);
        assertThat(mine.get("code").asInt()).as("作者可见 msg=" + text(mine, "msg")).isZero();
        assertThat(mine.get("data").get("status").asText()).isEqualTo("HIDDEN");
        assertThat(text(mine.get("data"), "statusReason")).isEqualTo("图片未通过审核");

        // 5. 再用违规图片当头像：451
        JsonNode avatar = put(token, "/member/me", Map.of("avatarUrl", url));
        assertThat(avatar.get("code").asInt()).as("头像违规 msg=" + text(avatar, "msg")).isEqualTo(451);
        assertThat(unsigned(text(get(token, "/member/me").get("data"), "avatarUrl"))).isNotEqualTo(unsigned(url));
        if (!oldAvatar.isEmpty()) put(token, "/member/me", Map.of("avatarUrl", oldAvatar));

        // 6. 咕嘟对话：输入命中禁发词 → 在调模型之前就被拦；落库的是占位文本而非原话
        JsonNode ses = post(token, "/agent/sessions", Map.of("title", P + "内容安全"));
        assertThat(ses.get("code").asInt()).as(ses.toString()).isZero();
        String sessionId = ses.get("data").get("sessionId").asText();
        boolean chatOpen = Boolean.TRUE.equals(get(token, "/feature-switches").get("data").get("agentChat").asBoolean());
        String sse = sendAgentMessage(token, sessionId, Map.of("text", "哪里能买到冰毒"), null);
        if (chatOpen) {
            // 开关打开：内容安全兜底，调用链状态为 GUARDED，没有任何模型片段
            assertThat(sse).contains("event:meta").contains("event:done").doesNotContain("event:tool_start");
            assertThat(sse).contains("event:token");
            JsonNode msgs = get(token, "/agent/sessions/" + sessionId + "/messages");
            assertThat(msgs.get("code").asInt()).isZero();
            assertThat(msgs.get("data").toString()).doesNotContain("冰毒").contains("已屏蔽").contains("内容安全检测");
            List<String> traceIds = jdbc.queryForList("SELECT id FROM agent_trace WHERE session_id = ?", String.class, sessionId);
            assertThat(traceIds).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT status FROM agent_trace WHERE id = ?", String.class, traceIds.get(0)))
                    .as("被内容安全拦截的业务状态").isEqualTo("GUARDED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_span WHERE trace_id = ? AND type = 'llm'",
                    Integer.class, traceIds.get(0))).as("没有调用过模型").isZero();
        } else {
            // 生产里本端咕嘟助手开关为关：在更前面就被 5032 拦下，同样没有放行到模型
            assertThat(sse).contains("5032").doesNotContain("event:token");
        }
        delete(token, "/agent/sessions/" + sessionId);
    }

    // ===================== Y. 小程序关闭厨房圈 =====================

    @Test
    void Y_小程序来源_厨房圈写接口403_读接口与其他端不受影响() throws Exception {
        String token = loginAdmin();
        // 先用 App 端（不带 X-Client）建一条自己的帖子，后面所有写操作都落在自建帖子 / 自己身上
        Map<String, Object> own = new HashMap<>();
        own.put("title", P + "App发帖");
        own.put("text", P + "App 端正常发帖");
        // 发帖至少要 1 张服务端签发的本站图片（任何环境都一样，原用例漏了；地址不要求真实存在）
        own.put("imageUrls", List.of(uploadSigner.sign("/gudu/uploads/e2e/community-y.jpg")));
        JsonNode ownPost = post(token, "/community/posts", own);
        assertThat(ownPost.get("code").asInt()).as("App 发帖 msg=" + text(ownPost, "msg")).isZero();
        long postId = ownPost.get("data").get("postId").asLong();

        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", token);
        h.set("X-Client", "mp-weixin");
        h.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> postBody = new HashMap<>();
        postBody.put("title", P + "小程序发帖");
        postBody.put("text", P + "小程序不该发出去");
        postBody.put("imageUrls", List.of(uploadSigner.sign("/gudu/uploads/e2e/community-y.jpg")));
        String[][] writes = {
                {"/community/posts", om.writeValueAsString(postBody)},
                {"/community/posts/" + postId + "/comments", "{\"text\":\"" + P + "评论\"}"},
                {"/community/posts/" + postId + "/reactions", "{\"type\":\"LIKE\",\"on\":true}"},
                {"/community/users/" + adminId + "/follow", "{\"on\":true}"},
                {"/community/reports", "{\"postId\":" + postId + ",\"reason\":\"" + P + "\"}"},
        };
        for (String[] w : writes) {
            ResponseEntity<String> resp = http.exchange(CTX + w[0], HttpMethod.POST, new HttpEntity<>(w[1], h),
                    String.class);
            assertThat(resp.getStatusCode().value()).as("小程序写 " + w[0]).isEqualTo(403);
            JsonNode body = om.readTree(resp.getBody());
            assertThat(body.get("code").asInt()).as(w[0]).isEqualTo(4031);
            assertThat(text(body, "msg")).contains("厨房圈");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_post WHERE title = ?", Integer.class,
                P + "小程序发帖")).as("未落库").isZero();

        // 读接口不受影响
        ResponseEntity<String> feed = http.exchange(CTX + "/community/feed", HttpMethod.GET, new HttpEntity<>(h),
                String.class);
        assertThat(feed.getStatusCode().value()).isEqualTo(200);
        assertThat(om.readTree(feed.getBody()).get("code").asInt()).isZero();

        // 不带 X-Client（App/H5）照常可写：对自己的帖子点赞
        JsonNode appReact = post(token, "/community/posts/" + postId + "/reactions", Map.of("type", "LIKE", "on", true));
        assertThat(appReact.get("code").asInt()).as("App 点赞 msg=" + text(appReact, "msg")).isNotEqualTo(4031);
        assertThat(appReact.get("code").asInt()).isZero();
        post(token, "/community/posts/" + postId + "/reactions", Map.of("type", "LIKE", "on", false));
    }

    // ===================== Z. 厨房圈紧急关停开关 =====================

    @Test
    void Z_厨房圈紧急关停_读写暂停_审核接口不受影响_留痕_再打开恢复() {
        String admin = loginAdmin();
        String chef = registerAndLogin(phoneFor("z_user"), "关停用户").get("token").asText();

        // 非超管不能切换
        JsonNode denied = put(chef, "/admin/community/switch", Map.of("enabled", false, "reason", P + "越权"));
        assertThat(denied.get("code").asInt()).as("非超管 msg=" + text(denied, "msg")).isNotZero();
        boolean originalOn = get(admin, "/admin/community/switch").get("data").get("enabled").asBoolean();
        assumeTrue(originalOn, "生产里厨房圈当前本来就是关闭状态，不做关停用例以免误改");
        assertThat(get(chef, "/community/feed").get("code").asInt()).as("仍开着").isZero();

        String offReason = P + "紧急关停" + UUID.randomUUID().toString().substring(0, 6);
        ProdE2ESnapshot snap = snapshot("sys_switch", "switch_key");
        try {
            JsonNode off = put(admin, "/admin/community/switch", Map.of("enabled", false, "reason", offReason));
            assertThat(off.get("code").asInt()).as("关闭 msg=" + text(off, "msg")).isZero();
            assertThat(off.get("data").get("enabled").asBoolean()).isFalse();
            assertThat(text(off.get("data"), "updatedByName")).isNotEmpty();
            assertThat(text(off.get("data"), "updatedAt")).isNotEmpty();

            // 用户侧读写统一 5031「厨房圈暂停服务」
            for (JsonNode r : List.of(get(chef, "/community/feed"), get(chef, "/community/topics"),
                    post(chef, "/community/posts", Map.of("title", P + "暂停期间发帖", "text", P + "x")))) {
                assertThat(r.get("code").asInt()).isEqualTo(5031);
                assertThat(text(r, "msg")).isEqualTo("厨房圈暂停服务");
            }
            // 管理后台审核接口不受影响
            assertThat(get(admin, "/admin/community/queue").get("code").asInt()).isZero();
            assertThat(get(admin, "/admin/community/stats").get("code").asInt()).isZero();

            // 修改留痕：操作人 + 时间 + 原因
            JsonNode state = get(admin, "/admin/community/switch");
            assertThat(state.get("data").get("recentChanges").get(0).get("reason").asText()).isEqualTo(offReason);
        } finally {
            // 先用接口改回（让运行中的实例刷新），再兜底校验库里恢复到快照
            JsonNode on = put(admin, "/admin/community/switch", Map.of("enabled", true, "reason", P + "恢复"));
            assertThat(on.get("code").asInt()).isZero();
            snap.restoreOrFail();
        }
        assertThat(get(chef, "/community/feed").get("code").asInt()).as("再打开恢复").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_switch_log WHERE switch_key = 'community.enabled' "
                + "AND enabled = 0 AND reason = ? AND operator_id IS NOT NULL", Integer.class, offReason)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_post WHERE title = ?", Integer.class,
                P + "暂停期间发帖")).isZero();
    }

    // ===================== 咕嘟助手 / 问咕嘟 紧急关停开关 =====================

    /** 当前（类别 × 端）开关有效状态：key 形如 agentChat|mp。 */
    private Map<String, Boolean> readSwitches(String admin) {
        JsonNode list = get(admin, "/admin/feature-switches");
        assertThat(list.get("code").asInt()).as("开关列表 msg=" + text(list, "msg")).isZero();
        assertThat(list.get("data")).hasSize(12);
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (JsonNode n : list.get("data")) {
            m.put(text(n, "category") + "|" + text(n, "platform"), n.get("enabled").asBoolean());
        }
        return m;
    }

    /** 对比本端用户侧状态与期望（生产里 App 端开关可能本来就是关）。 */
    private void assertPublicStatus(String token, String client, Map<String, Boolean> expect) {
        String platform = client == null ? "app" : "mp";
        JsonNode st = withClient(client, token, "/feature-switches", HttpMethod.GET, null);
        for (String f : new String[]{"agentChat", "cookAsk", "vision", "mealPlan", "estimate", "postText"}) {
            assertThat(st.get("data").get(f).asBoolean()).as(f + " client=" + client)
                    .isEqualTo(expect.get(f + "|" + platform));
        }
    }

    @Test
    void Z2_生成式AI紧急关停_按类别按端_只拦生成_状态接口按端_一键关闭_留痕_再打开恢复() throws Exception {
        String admin = loginAdmin();
        String chef = registerAndLogin(phoneFor("z2_user"), "开关用户").get("token").asText();
        String tag = UUID.randomUUID().toString().substring(0, 6);

        // 非运营不能切换；用户侧状态接口免登录（游客也可读）
        JsonNode denied = put(chef, "/admin/feature-switches",
                Map.of("key", "agent.chat.enabled.app", "enabled", false, "reason", P + "越权"));
        assertThat(denied.get("code").asInt()).as("非运营 msg=" + text(denied, "msg")).isNotZero();
        Map<String, Boolean> orig = readSwitches(admin);
        for (String client : new String[]{null, "mp-weixin"}) {
            assertPublicStatus(chef, client, orig);
        }
        // 游客（不带登录态）也能读本端状态：只有六个布尔值，无用户信息
        JsonNode guest = withClient("mp-weixin", null, "/feature-switches", HttpMethod.GET, null);
        assertThat(guest.get("code").asInt()).as("游客可读 msg=" + text(guest, "msg")).isZero();
        assertThat(guest.get("data").size()).isEqualTo(6);
        assertThat(guest.get("data").get("agentChat").asBoolean()).isEqualTo(orig.get("agentChat|mp"));

        String sid = post(chef, "/agent/sessions", Map.of("title", P + "开关")).get("data").get("sessionId").asText();
        ProdE2ESnapshot snap = snapshot("sys_switch", "switch_key");
        try {
            // 关停必须填原因；未知键 / 无端后缀的旧键拒绝（不会改库）
            assertThat(put(admin, "/admin/feature-switches", Map.of("key", "agent.chat.enabled.mp", "enabled", false))
                    .get("code").asInt()).isNotZero();
            assertThat(put(admin, "/admin/feature-switches",
                    Map.of("key", "agent.chat.enabled", "enabled", false, "reason", P + "旧键")).get("code").asInt())
                    .isNotZero();
            assertThat(readSwitches(admin)).as("被拒绝的请求不改库").isEqualTo(orig);

            // 只关小程序的咕嘟助手：小程序被拦(5032)，App 状态不受影响
            String offReason = P + "紧急关停" + tag;
            JsonNode off = put(admin, "/admin/feature-switches",
                    Map.of("key", "agent.chat.enabled.mp", "enabled", false, "reason", offReason));
            assertThat(off.get("code").asInt()).as("关闭 msg=" + text(off, "msg")).isZero();
            assertThat(off.get("data").get("enabled").asBoolean()).isFalse();
            assertThat(off.get("data").get("platform").asText()).isEqualTo("mp");

            JsonNode stMp = withClient("mp-weixin", chef, "/feature-switches", HttpMethod.GET, null);
            assertThat(stMp.get("data").get("agentChat").asBoolean()).isFalse();
            assertThat(stMp.get("data").get("cookAsk").asBoolean()).as("问咕嘟独立").isEqualTo(orig.get("cookAsk|mp"));
            JsonNode stApp = withClient(null, chef, "/feature-switches", HttpMethod.GET, null);
            assertThat(stApp.get("data").get("agentChat").asBoolean()).as("App 不受小程序开关影响")
                    .isEqualTo(orig.get("agentChat|app"));

            String blocked = sendAgentMessage(chef, sid, Map.of("text", P + "你好"), "mp-weixin");
            assertThat(blocked).contains("event:error").contains("5032").contains("咕嘟助手暂停服务");
            String cook = sendAgentMessage(chef, sid, Map.of("text", P + "这步怎么做", "context", Map.of("page", "cook_mode")),
                    "mp-weixin");
            // 咕嘟助手关停 → cook_mode 也被拦（不看自报的 page；需两个开关都开）
            assertThat(cook).contains("5032").doesNotContain("5033");

            // 不拦：会话与历史查看
            assertThat(get(chef, "/agent/sessions/" + sid + "/messages").get("code").asInt()).isZero();
            assertThat(get(chef, "/agent/sessions").get("code").asInt()).isZero();

            // 恢复咕嘟助手，再测问咕嘟（小程序）关停：只拦 cook_mode（普通对话是否放行用状态接口验证，避免触发模型）
            assertThat(put(admin, "/admin/feature-switches",
                    Map.of("key", "agent.chat.enabled.mp", "enabled", orig.get("agentChat|mp"), "reason", P + "恢复" + tag))
                    .get("code").asInt()).isZero();
            assumeTrue(orig.get("agentChat|mp"), "生产里小程序咕嘟助手本来就是关，无法单独验证问咕嘟关停");
            assertThat(put(admin, "/admin/feature-switches",
                    Map.of("key", "agent.cook_ask.enabled.mp", "enabled", false, "reason", P + "问咕嘟关停" + tag))
                    .get("code").asInt()).isZero();
            String cookBlocked = sendAgentMessage(chef, sid, Map.of("text", P + "这步怎么做", "context", Map.of("page", "cook_mode")),
                    "mp-weixin");
            assertThat(cookBlocked).contains("5033").contains("问咕嘟暂停服务");
            JsonNode stCook = withClient("mp-weixin", chef, "/feature-switches", HttpMethod.GET, null);
            assertThat(stCook.get("data").get("agentChat").asBoolean()).as("普通对话不受问咕嘟开关影响").isTrue();
            assertThat(stCook.get("data").get("cookAsk").asBoolean()).isFalse();
            assertThat(put(admin, "/admin/feature-switches",
                    Map.of("key", "agent.cook_ask.enabled.mp", "enabled", orig.get("cookAsk|mp"), "reason", P + "恢复" + tag))
                    .get("code").asInt()).isZero();

            // 拍照识别 / 一周餐程 / 营养估算：小程序一键关闭全部（同一原因，逐个记日志）；窗口内只发被拦的三个请求
            JsonNode noReasonAll = put(admin, "/admin/feature-switches/batch", Map.of("platform", "mp", "enabled", false));
            assertThat(noReasonAll.get("code").asInt()).isNotZero();
            String allReason = P + "小程序全关" + tag;
            try {
                JsonNode all = put(admin, "/admin/feature-switches/batch",
                        Map.of("platform", "mp", "enabled", false, "reason", allReason));
                assertThat(all.get("code").asInt()).as("batch msg=" + text(all, "msg")).isZero();
                assertThat(all.get("data")).hasSize(6);

                JsonNode visionMp = withClient("mp-weixin", chef, "/vision/jobs", HttpMethod.POST,
                        Map.of("imageUrl", "https://example.com/e2e/x.jpg", "mode", "FRIDGE"));
                assertThat(visionMp.get("code").asInt()).isEqualTo(5034);
                JsonNode planMp = withClient("mp-weixin", chef, "/mealplan/drafts", HttpMethod.POST, Map.of());
                assertThat(planMp.get("code").asInt()).isEqualTo(5035);
                // 营养估算：小程序端被拦(5036)，不调模型（用超管家庭身份，有 ai.use 权限）
                JsonNode estMp = withClient("mp-weixin", admin, "/ai/dish/estimate", HttpMethod.POST,
                        Map.of("description", P + "一盘番茄炒蛋", "servingFactor", 1));
                assertThat(estMp.get("code").asInt()).as("estimate mp msg=" + text(estMp, "msg")).isEqualTo(5036);
                // 营养报告不是生成式，不拦
                assertThat(withClient("mp-weixin", chef, "/nutrition/report", HttpMethod.GET, null).get("code").asInt())
                        .isNotEqualTo(5035);
            } finally {
                // 立刻把小程序六项改回原值，缩短窗口
                for (String c : new String[]{"agentChat", "cookAsk", "vision", "mealPlan", "estimate", "postText"}) {
                    put(admin, "/admin/feature-switches", Map.of("key", switchKey(c, "mp"), "enabled", orig.get(c + "|mp"),
                            "reason", P + "恢复" + tag));
                }
            }

            // 修改留痕（带端后缀的键）
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_switch_log WHERE switch_key = 'agent.chat.enabled.mp' "
                    + "AND enabled = 0 AND reason = ? AND operator_id IS NOT NULL", Integer.class, offReason)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_switch_log WHERE switch_key LIKE '%.mp' "
                    + "AND enabled = 0 AND reason = ?", Integer.class, allReason)).isEqualTo(6);
            assertThat(get(admin, "/admin/feature-switches").get("data")).hasSize(12);

            // AI 帮写（App 端关停 → 不带 X-Client 的 ai-text 返回 5037；小程序端该接口另被厨房圈守卫拦，故用 App 端验）
            assertThat(put(admin, "/admin/feature-switches",
                    Map.of("key", "ai.post_text.enabled.app", "enabled", false, "reason", P + "帮写关停" + tag)).get("code").asInt())
                    .isZero();
            JsonNode textApp = withClient(null, admin, "/community/drafts/ai-text", HttpMethod.POST, Map.of("style", "short"));
            assertThat(textApp.get("code").asInt()).as("ai-text msg=" + text(textApp, "msg")).isEqualTo(5037);

            // App 端营养估算关停：普通接口（一餐估算）被拦。
            // 「运营在管理后台补全食材营养不受限」会真实调用大模型，不在本类验证（归 AI 用例窗口）
            assertThat(put(admin, "/admin/feature-switches",
                    Map.of("key", "ai.estimate.enabled.app", "enabled", false, "reason", P + "估算关停" + tag)).get("code").asInt())
                    .isZero();
            JsonNode estApp = withClient(null, admin, "/ai/dish/estimate", HttpMethod.POST,
                    Map.of("description", P + "一盘番茄炒蛋", "servingFactor", 1));
            assertThat(estApp.get("code").asInt()).isEqualTo(5036);
        } finally {
            // 先用接口把所有与原值不同的开关改回，再兜底校验库里与快照一致（并删掉测试新增的行）
            try {
                Map<String, Boolean> now = readSwitches(admin);
                for (Map.Entry<String, Boolean> e : orig.entrySet()) {
                    if (!e.getValue().equals(now.get(e.getKey()))) {
                        String[] kp = e.getKey().split("\\|");
                        put(admin, "/admin/feature-switches", Map.of("key", switchKey(kp[0], kp[1]), "enabled", e.getValue(),
                                "reason", P + "恢复" + tag));
                    }
                }
            } finally {
                delete(chef, "/agent/sessions/" + sid);
                snap.restoreOrFail();
            }
        }
        assertThat(readSwitches(admin)).as("全部恢复为原值").isEqualTo(orig);
        for (String client : new String[]{null, "mp-weixin"}) {
            assertPublicStatus(chef, client, orig);
        }
    }

    private static String switchKey(String category, String platform) {
        String base = switch (category) {
            case "agentChat" -> "agent.chat.enabled";
            case "cookAsk" -> "agent.cook_ask.enabled";
            case "vision" -> "ai.vision.enabled";
            case "mealPlan" -> "ai.mealplan.enabled";
            case "estimate" -> "ai.estimate.enabled";
            case "postText" -> "ai.post_text.enabled";
            default -> throw new IllegalArgumentException(category);
        };
        return base + "." + platform;
    }

    // ===================== AI 用量与额度 =====================

    @Test
    void Z3_AI用量与额度_非运营拒绝_概览结构_改限额留痕_非法值拒绝_改完还原() throws Exception {
        String admin = loginAdmin();
        String chef = registerAndLogin(phoneFor("z3_user"), "额度用户").get("token").asText();

        // 非运营不能看也不能改
        assertThat(get(chef, "/admin/ai-guard").get("code").asInt()).isNotZero();
        assertThat(put(chef, "/admin/ai-guard", Map.of("dailyBudgetYuan", 1)).get("code").asInt()).isNotZero();

        JsonNode ov = get(admin, "/admin/ai-guard");
        assertThat(ov.get("code").asInt()).as("概览 msg=" + text(ov, "msg")).isZero();
        JsonNode d = ov.get("data");
        assertThat(d.has("spentYuan") && d.has("limitYuan") && d.has("percent") && d.has("level")).isTrue();
        assertThat(d.get("level").asText()).isIn("NORMAL", "WARN", "PAUSED");
        assertThat(d.get("categories")).hasSize(4);
        assertThat(d.get("categories").get(0).get("key").asText()).isEqualTo("vision");
        BigDecimal oldBudget = new BigDecimal(d.get("limitYuan").asText());
        int oldVision = d.get("categories").get(0).get("limitPerAccount").asInt();
        int oldMealplan = d.get("categories").get(1).get("limitPerAccount").asInt();
        // 生产里只往宽松方向改（预算 +1、次数 +1），绝不把全站额度调紧导致真实用户被拦
        BigDecimal newBudget = oldBudget.add(BigDecimal.ONE);

        ProdE2ESnapshot snap = snapshot("ai_guard_config", "config_key");
        try {
            // 非法值被拒绝，配置不变
            assertThat(put(admin, "/admin/ai-guard", Map.of("dailyBudgetYuan", -5)).get("code").asInt()).isNotZero();
            assertThat(put(admin, "/admin/ai-guard", Map.of("warnPercent", 0)).get("code").asInt()).isNotZero();
            assertThat(put(admin, "/admin/ai-guard", Map.of("limits", Map.of("vision", -1))).get("code").asInt()).isNotZero();
            JsonNode unchanged = get(admin, "/admin/ai-guard").get("data");
            assertThat(new BigDecimal(unchanged.get("limitYuan").asText())).isEqualByComparingTo(oldBudget);
            assertThat(unchanged.get("categories").get(0).get("limitPerAccount").asInt()).isEqualTo(oldVision);

            // 合法修改：立即在本实例反映，日志含新旧值与操作人
            JsonNode upd = put(admin, "/admin/ai-guard", Map.of("dailyBudgetYuan", newBudget,
                    "limits", Map.of("vision", oldVision + 1, "mealplan", oldMealplan + 1)));
            assertThat(upd.get("code").asInt()).as("修改 msg=" + text(upd, "msg")).isZero();
            assertThat(new BigDecimal(upd.get("data").get("limitYuan").asText())).isEqualByComparingTo(newBudget);
            assertThat(upd.get("data").get("categories").get(0).get("limitPerAccount").asInt()).isEqualTo(oldVision + 1);
            JsonNode logs = upd.get("data").get("logs");
            assertThat(logs.size()).isGreaterThanOrEqualTo(3);
            assertThat(logs.get(0).get("operatorId").asLong()).isPositive();
            assertThat(logs.get(0).has("oldValue") && logs.get(0).has("newValue")).isTrue();
        } finally {
            put(admin, "/admin/ai-guard", Map.of("dailyBudgetYuan", oldBudget,
                    "limits", Map.of("vision", oldVision, "mealplan", oldMealplan)));
            snap.restoreOrFail();
        }
        JsonNode back = get(admin, "/admin/ai-guard").get("data");
        assertThat(new BigDecimal(back.get("limitYuan").asText())).isEqualByComparingTo(oldBudget);
        assertThat(back.get("categories").get(0).get("limitPerAccount").asInt()).isEqualTo(oldVision);
        assertThat(back.get("categories").get(1).get("limitPerAccount").asInt()).isEqualTo(oldMealplan);
    }

    // ===================== 隐私：单独同意 / 导出 / 保存期限 =====================

    @Test
    void Q_单独同意_撤回即删除_健康档案与手机号加密存储() {
        String token = loginAdmin();
        JsonNode st = get(token, "/consent/sensitive");
        assertThat(st.get("code").asInt()).isZero();
        assertThat(st.get("data").get("items").get("sensitive_health").get("granted").asBoolean()).isFalse();

        // 未同意：健康档案 428，不保存
        JsonNode denied = post(token, "/member", Map.of("name", P + "敏感成员", "healthProfile", Map.of("height", 170)));
        assertThat(denied.get("code").asInt()).as(denied.toString()).isEqualTo(428);
        assertThat(denied.get("data").get("docTypes").toString()).contains("sensitive_health");

        assertThat(post(token, "/consent/sensitive", Map.of("docTypes", List.of("sensitive_health"), "channel", "e2e"))
                .get("code").asInt()).isZero();
        long adult = post(token, "/member", Map.of("name", P + "敏感成员", "healthProfile", Map.of("height", 170)))
                .get("data").asLong();
        String stored = jdbc.queryForObject("SELECT health_profile FROM member WHERE id = ?", String.class, adult);
        assertThat(stored).as("健康档案密文落库").startsWith("ENC:v").doesNotContain("170");
        assertThat(jdbc.queryForObject("SELECT health_consent_account_id FROM member WHERE id = ?", Long.class, adult))
                .isNotNull();
        assertThat(get(token, "/member?pageNum=1&pageSize=100").get("data").toString()).contains("\"height\":170");

        // 儿童：另需监护人同意
        Map<String, Object> babyBody = Map.of("name", P + "宝宝",
                "healthProfile", Map.of("ageMonth", 10, "audiences", List.of("宝宝辅食")));
        JsonNode babyDenied = post(token, "/member", babyBody);
        assertThat(babyDenied.get("code").asInt()).isEqualTo(428);
        assertThat(babyDenied.get("data").get("docTypes").toString()).contains("child_info");
        post(token, "/consent/sensitive", Map.of("docTypes", List.of("child_info"), "channel", "e2e"));
        long baby = post(token, "/member", babyBody).get("data").asLong();

        // 撤回儿童同意：只删儿童档案的健康字段
        assertThat(post(token, "/consent/sensitive/withdraw", Map.of("docType", "child_info")).get("code").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT health_profile FROM member WHERE id = ?", String.class, baby)).isNull();
        assertThat(jdbc.queryForObject("SELECT health_profile FROM member WHERE id = ?", String.class, adult)).isNotNull();
        // 撤回健康档案同意：其提供的全部健康档案清空；再保存 428
        post(token, "/consent/sensitive/withdraw", Map.of("docType", "sensitive_health"));
        assertThat(jdbc.queryForObject("SELECT health_profile FROM member WHERE id = ?", String.class, adult)).isNull();
        assertThat(put(token, "/member", Map.of("id", adult, "healthProfile", Map.of("weight", 60)))
                .get("code").asInt()).isEqualTo(428);

        // 手机号：代建账号后 account.phone 为密文 + 盲索引，按手机号登录仍可用
        String encPhone = phoneFor("q_enc");
        JsonNode created = post(token, "/member", Map.of("name", P + "加密账号", "phone", encPhone, "password", PW));
        assertThat(created.get("code").asInt()).as(created.toString()).isZero();
        long encAccountId = jdbc.queryForObject("SELECT account_id FROM member WHERE id = ?", Long.class,
                created.get("data").asLong());
        ProdE2ELedger.recordAccount(encAccountId); // 该账号不一定带 E2E 昵称，记台账保证清理
        Map<String, Object> acc = jdbc.queryForMap(
                "SELECT a.phone, a.phone_hash FROM account a JOIN member m ON m.account_id = a.id WHERE m.id = ?",
                created.get("data").asLong());
        assertThat((String) acc.get("phone")).startsWith("ENC:v").doesNotContain(encPhone);
        assertThat((String) acc.get("phone_hash")).hasSize(64);
        assertThat(loginAs(encPhone, PW)).isNotBlank();
    }

    @Test
    void R_导出我的数据_每天3次_临时下载链接() {
        // 每日 3 次限流：用自建新账号
        String phone = phoneFor("r_export");
        String token = registerAndLogin(phone, "导出账号").get("token").asText();
        post(token, "/consent/sensitive", Map.of("docTypes", List.of("sensitive_health"), "channel", "e2e"));
        String adminMemberName = jdbc.queryForObject("SELECT name FROM member WHERE id = ?", String.class, chefMemberId);

        JsonNode out = get(token, "/account/me/export");
        assertThat(out.get("code").asInt()).as(out.toString()).isZero();
        JsonNode d = out.get("data");
        assertThat(d.get("account").get("phone").asText()).isEqualTo(phone);
        assertThat(d.get("families").isArray()).isTrue();
        assertThat(d.get("families").toString()).as("不含其他家庭的成员档案").doesNotContain(adminMemberName)
                .doesNotContain(P + "敏感成员");
        for (String k : List.of("dishes", "cookingRecords", "dailyLogs", "reviews", "aiConversations", "feedback")) {
            assertThat(d.get(k).isArray()).as(k).isTrue();
        }
        assertThat(d.get("consents").toString()).contains("sensitive_health");

        JsonNode link = post(token, "/account/me/export/link", null);
        assertThat(link.get("code").asInt()).isZero();
        String path = link.get("data").get("path").asText();
        assertThat(path).startsWith("/account/export/download?token=");
        ResponseEntity<String> file = http.exchange(CTX + path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(file.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        assertThat(file.getBody()).contains(phone);
        assertThat(get(null, "/account/export/download?token=bad-token-0123456789abcdef").get("code").asInt())
                .isEqualTo(404);

        assertThat(get(token, "/account/me/export").get("code").asInt()).isZero();
        assertThat(get(token, "/account/me/export").get("code").asInt()).as("第 4 次").isEqualTo(429);
    }

    /**
     * 原用例会往 ai_call_log / feedback 插超期数据后调用 DataRetentionJob.runOnce，
     * 而 runOnce 是全站批处理，会在生产物理删除所有真实的超期 AI 日志 / 对话 / 反馈，生产绝不能跑。
     * 清理逻辑本身（期限计算、分批、覆盖、失败隔离）由 DataRetentionJobTest 等单测覆盖，这里改为只读配置检查（见下一个用例）。
     */
    @Disabled("DataRetentionJob.runOnce 会在生产删除真实超期数据；清理逻辑已由 DataRetentionJobTest 单测覆盖，生产改用 S_保存期限配置_只读检查")
    @Test
    void S_保存期限清理_超期删除_未超期与未处理保留() {
        // 刻意留空：不得在生产调用 retentionJob.runOnce()
    }

    @Test
    void S_保存期限配置_只读检查_生产测试进程不执行清理() {
        // 本 profile 关闭保存期限清理，且测试进程里没有定时器处理器（基类已断言），所以 03:30 任务也不会跑
        assertThat(env.getProperty("gudu.retention.enabled")).as("prod-e2e profile 必须关闭保存期限清理").isEqualTo("false");
        Object target = AopTestUtils.getTargetObject(retentionJob);
        assertThat(ReflectionTestUtils.getField(target, "enabled")).isEqualTo(false);
        // 保存期限相关 Bean 与策略存在，默认期限与隐私政策一致
        assertThat(purgers).isNotEmpty();
        Map<String, Integer> days = new LinkedHashMap<>();
        for (RetentionPurger p : purgers) days.put(p.policy(), env.getProperty("gudu.retention.days." + p.policy(),
                Integer.class, p.defaultDays()));
        assertThat(days.keySet()).contains("agent-conversation", "ai-call-log", "feedback");
        assertThat(days.get("agent-conversation")).isEqualTo(180);
        assertThat(days.get("ai-call-log")).isEqualTo(180);
        assertThat(days.get("feedback")).isEqualTo(365);
        // 保存期限清理依赖的表存在（只读）
        for (String t : List.of("ai_call_log", "feedback", "agent_message", "agent_session")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                    + "AND table_name = ?", Integer.class, t)).as("表 " + t).isEqualTo(1);
        }
    }

    // ===================== Y. 发布合规：协议同意上报 + 运营后台 F.1–F.4 =====================

    @Test
    void Y1_协议同意上报_后台按账号查询_非运营调后台接口403() {
        String admin = loginAdminScope();
        String phone = phoneFor("y1");
        JsonNode login = registerAndLogin(phone, "同意用户");
        long accountId = login.get("accountId").asLong();
        String user = login.get("token").asText();

        // B.1 登录成功后上报：默认 terms + privacy 各一条；撤回记 granted=false；单独同意类型不能走此入口
        JsonNode c = post(user, "/consent", Map.of("docVersion", "2026-10-26", "channel", "mp-weixin"));
        assertThat(c.get("code").asInt()).as(c.toString()).isZero();
        assertThat(c.get("data")).hasSize(2);
        assertThat(post(user, "/consent", Map.of("docVersion", "2026-10-26", "docTypes", List.of("privacy"),
                "granted", false)).get("code").asInt()).isZero();
        assertThat(post(user, "/consent", Map.of("docVersion", "2026-10-26",
                "docTypes", List.of("sensitive_health"))).get("code").asInt()).isNotZero();
        assertThat(post(user, "/consent", Map.of("channel", "mp-weixin")).get("code").asInt()).as("缺版本").isNotZero();

        // F.4 后台按账号查询：倒序，最新一条为撤回
        JsonNode hist = get(admin, "/admin/accounts/" + accountId + "/consents");
        assertThat(hist.get("code").asInt()).as(hist.toString()).isZero();
        assertThat(hist.get("data")).hasSize(3);
        assertThat(text(hist.get("data").get(0), "docType")).isEqualTo("privacy");
        assertThat(hist.get("data").get(0).get("granted").asBoolean()).isFalse();
        assertThat(hasRecord(hist.get("data"), "docType", "terms")).isTrue();

        // 非 OPERATOR / SUPER_ADMIN：全部后台接口 403
        for (String path : List.of("/admin/accounts", "/admin/deactivations", "/admin/content-violations/media",
                "/admin/content-violations/text", "/admin/operation-logs", "/admin/accounts/" + accountId + "/consents")) {
            assertThat(get(user, path).get("code").asInt()).as(path).isEqualTo(403);
        }
        assertThat(post(user, "/admin/accounts/" + accountId + "/ban", Map.of("reason", P + "越权")).get("code").asInt())
                .isEqualTo(403);
        // 普通用户不能用 scope=admin 登录后台
        JsonNode adminLogin = post(null, "/auth/login", Map.of("username", phone, "password", PW, "scope", "admin"));
        assertThat(adminLogin.get("code").asInt()).isEqualTo(403);
    }

    @Test
    void Y2_账号列表_封禁下线_登录与写接口423_解封恢复_操作日志() {
        String admin = loginAdminScope();
        String phone = phoneFor("y2");
        String nickname = P + "封禁用户";
        JsonNode login = registerAndLogin(phone, "封禁用户");
        long accountId = login.get("accountId").asLong();
        String user = login.get("token").asText();

        // F.1 查询：昵称关键字；只返回元数据（家庭数，不含家庭数据）
        JsonNode list = get(admin, "/admin/accounts?keyword=" + nickname);
        assertThat(list.get("code").asInt()).as(list.toString()).isZero();
        JsonNode row = null;
        for (JsonNode r : list.get("data").get("records")) {
            if (r.get("id").asLong() == accountId) row = r;
        }
        assertThat(row).as("按昵称关键字查到自建账号").isNotNull();
        assertThat(row.get("familyCount").asInt()).isGreaterThanOrEqualTo(1);
        // 所属家庭：返回家庭名称与角色（自建账号注册即为自己家庭的主理人），且只有元数据
        JsonNode families = row.get("families");
        assertThat(families).as("families 列表").isNotNull();
        assertThat(families.size()).isEqualTo(row.get("familyCount").asInt());
        JsonNode fam0 = families.get(0);
        assertThat(text(fam0, "name")).isNotBlank();
        assertThat(text(fam0, "role")).isEqualTo("OWNER");
        assertThat(fam0.get("familyId").asLong()).isPositive();
        assertThat(fam0.fieldNames()).toIterable().containsExactlyInAnyOrder("familyId", "name", "role", "status");
        assertThat(row.get("banned").asBoolean()).isFalse();
        assertThat(text(row, "maskedPhone")).isEqualTo(phone.substring(0, 3) + "****" + phone.substring(7));
        assertThat(row.toString()).doesNotContain(phone);

        // 封禁：原因必填；成功后旧 token 下线、重新登录 423
        assertThat(post(admin, "/admin/accounts/" + accountId + "/ban", Map.of("reason", " ")).get("code").asInt())
                .isNotZero();
        JsonNode ban = post(admin, "/admin/accounts/" + accountId + "/ban", Map.of("reason", P + " 刷广告"));
        assertThat(ban.get("code").asInt()).as(ban.toString()).isZero();
        assertThat(post(user, "/feedback", Map.of("content", P + "封禁后反馈")).get("code").asInt())
                .as("封禁后旧 token 已下线").isEqualTo(401);
        JsonNode relogin = post(null, "/auth/login", Map.of("username", phone, "password", PW));
        assertThat(relogin.get("code").asInt()).isEqualTo(423);
        assertThat(text(relogin, "msg")).contains(P + " 刷广告");
        JsonNode bannedOnly = get(admin, "/admin/accounts?bannedOnly=true&keyword=" + nickname);
        JsonNode bannedRow = null;
        for (JsonNode r : bannedOnly.get("data").get("records")) {
            if (r.get("id").asLong() == accountId) bannedRow = r;
        }
        assertThat(bannedRow).isNotNull();
        assertThat(bannedRow.get("banned").asBoolean()).isTrue();

        // 不能封禁超管 / 自己（拒绝在业务校验处发生，不会改动夹具账号）
        assertThat(post(admin, "/admin/accounts/" + adminId + "/ban", Map.of("reason", P + " 自封")).get("code").asInt())
                .isNotZero();

        // 解封：恢复登录与写接口
        assertThat(post(admin, "/admin/accounts/" + accountId + "/unban", Map.of("reason", P + " 申诉通过"))
                .get("code").asInt()).isZero();
        String again = loginAs(phone, PW);
        assertThat(post(again, "/feedback", Map.of("content", P + "解封后反馈")).get("code").asInt()).isZero();

        // 操作日志：封禁 + 解封各一条
        JsonNode logs = get(admin, "/admin/operation-logs?targetType=ACCOUNT&targetId=" + accountId);
        assertThat(logs.get("code").asInt()).isZero();
        assertThat(logs.get("data").get("total").asLong()).isEqualTo(2);
        assertThat(hasRecord(logs.get("data").get("records"), "action", "BAN")).isTrue();
        assertThat(hasRecord(logs.get("data").get("records"), "action", "UNBAN")).isTrue();

        // 操作日志筛选：操作人 + 日期区间（含当天）；起止颠倒 / 格式错误 → 业务错误
        String today = LocalDate.now().toString();
        JsonNode byOperator = get(admin, "/admin/operation-logs?targetType=ACCOUNT&targetId=" + accountId
                + "&operatorId=" + logs.get("data").get("records").get(0).get("operatorId").asLong()
                + "&from=" + today + "&to=" + today);
        assertThat(byOperator.get("code").asInt()).isZero();
        assertThat(byOperator.get("data").get("total").asLong()).isEqualTo(2);
        JsonNode future = get(admin, "/admin/operation-logs?targetType=ACCOUNT&targetId=" + accountId
                + "&from=" + LocalDate.now().plusDays(2));
        assertThat(future.get("data").get("total").asLong()).isZero();
        assertThat(get(admin, "/admin/operation-logs?from=2099-01-02&to=2099-01-01").get("code").asInt()).isNotZero();
        assertThat(get(admin, "/admin/operation-logs?from=abc").get("code").asInt()).isNotZero();
    }

    @Test
    void Y3_注销处理记录_匿名化步骤完成_完成后不可重试() {
        String admin = loginAdminScope();
        JsonNode login = registerAndLogin(phoneFor("y3"), "注销用户");
        long accountId = login.get("accountId").asLong();
        String user = login.get("token").asText();
        assertThat(post(user, "/feedback", Map.of("content", P + "注销前反馈", "contact", "wx:e2e-dead")).get("code")
                .asInt()).isZero();
        assertThat(delete(user, "/account/me").get("code").asInt()).isZero();

        JsonNode rec = null;
        for (int pageNum = 1; pageNum <= 30 && rec == null; pageNum++) {
            JsonNode page = get(admin, "/admin/deactivations?pageSize=100&pageNum=" + pageNum);
            assertThat(page.get("code").asInt()).as(page.toString()).isZero();
            for (JsonNode r : page.get("data").get("records")) {
                if (r.get("accountId").asLong() == accountId) rec = r;
            }
            if (page.get("data").get("records").size() < 100) break;
        }
        assertThat(rec).as("注销记录").isNotNull();
        assertThat(text(rec, "status")).isEqualTo("DONE");
        assertThat(rec.get("overdue").asBoolean()).isFalse();
        assertThat(rec.get("steps").toString()).contains("账号匿名化与退出家庭").contains("意见反馈联系方式与截图清除");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback WHERE member_id = ? AND contact IS NOT NULL",
                Integer.class, accountId)).as("反馈联系方式已清除").isZero();
        assertThat(post(admin, "/admin/deactivations/" + rec.get("id").asLong() + "/retry", null).get("code").asInt())
                .as("已完成不可重试").isNotZero();
        assertThat(get(admin, "/admin/deactivations?overdue=true").get("code").asInt()).isZero();
    }

    @Test
    void Y4_违规复核_文本确认_图片恢复还原文件与头像_确认违规() throws Exception {
        String admin = loginAdminScope();
        JsonNode login = registerAndLogin(phoneFor("y4"), "违规用户");
        long accountId = login.get("accountId").asLong();
        String user = login.get("token").asText();

        // 文本：昵称命中禁发词 → 451 且留痕（独立事务，不随 451 回滚）
        assertThat(put(user, "/member/me", Map.of("name", "网赌一哥")).get("code").asInt()).isEqualTo(451);
        JsonNode texts = get(admin, "/admin/content-violations/text?accountId=" + accountId);
        assertThat(texts.get("code").asInt()).as(texts.toString()).isZero();
        JsonNode tv = texts.get("data").get("records").get(0);
        assertThat(text(tv, "purpose")).isEqualTo("NICKNAME");
        assertThat(tv.get("confirmed").asBoolean()).isFalse();
        JsonNode tc = post(admin, "/admin/content-violations/text/" + tv.get("id").asLong() + "/confirm",
                Map.of("reason", P + " 确认"));
        assertThat(tc.get("data").get("confirmed").asBoolean()).isTrue();

        // 图片：上传头像 → 模拟微信回调判违规（隔离 + 清头像）
        String url = uploadPng(user, "e2e-prod-review.png");
        String servedPath = url.replaceFirst("^/gudu", "");
        assertThat(put(user, "/member/me", Map.of("avatarUrl", url)).get("code").asInt()).isZero();
        // 上传会重新编码去 EXIF，落盘字节不再等于原始 png；以「违规前实际对外提供的字节」为基准比较隔离 / 还原
        byte[] stored = http.getForEntity(CTX + servedPath, byte[].class).getBody();
        assertThat(stored).as("上传后原图可读").isNotEmpty();
        String traceId = newTraceId();
        jdbc.update("INSERT INTO content_media_check(trace_id, media_url, member_id, purpose, status) "
                + "VALUES (?, ?, ?, 'AVATAR', 'PENDING')", traceId, unsigned(url), accountId);
        pushMediaVerdict(traceId, "n2", false);
        assertThat(text(get(user, "/member/me").get("data"), "avatarUrl")).as("违规头像已清空").isEmpty();
        assertThat(http.getForEntity(CTX + servedPath, byte[].class).getBody()).as("原地址为占位图").isNotEqualTo(stored);

        // 后台列表：待复核；可查看隔离原图
        JsonNode media = get(admin, "/admin/content-violations/media?review=PENDING&accountId=" + accountId);
        assertThat(media.get("code").asInt()).as(media.toString()).isZero();
        long mid = media.get("data").get("records").get(0).get("id").asLong();
        assertThat(text(get(admin, "/admin/content-violations/media/" + mid + "/image"), "data"))
                .startsWith("data:image/png;base64,");

        // 恢复（误判）：原因必填；还原文件、恢复头像；再恢复拒绝；恢复后不能确认违规
        assertThat(post(admin, "/admin/content-violations/media/" + mid + "/restore", Map.of("reason", ""))
                .get("code").asInt()).isNotZero();
        JsonNode restored = post(admin, "/admin/content-violations/media/" + mid + "/restore",
                Map.of("reason", P + " 误判"));
        assertThat(restored.get("code").asInt()).as(restored.toString()).isZero();
        assertThat(restored.get("data").get("filesRestored").asBoolean()).isTrue();
        assertThat(http.getForEntity(CTX + servedPath, byte[].class).getBody()).as("原图已还原为违规前的字节").isEqualTo(stored);
        assertThat(unsigned(text(get(user, "/member/me").get("data"), "avatarUrl"))).isEqualTo(unsigned(url));
        assertThat(put(user, "/member/me", Map.of("avatarUrl", url)).get("code").asInt()).as("恢复后可再用作头像")
                .isZero();
        assertThat(post(admin, "/admin/content-violations/media/" + mid + "/restore", Map.of("reason", P + " 再恢复"))
                .get("code").asInt()).isNotZero();
        assertThat(post(admin, "/admin/content-violations/media/" + mid + "/confirm", Map.of("reason", P + " 改判"))
                .get("code").asInt()).isNotZero();

        JsonNode logs = get(admin, "/admin/operation-logs?targetType=MEDIA_CHECK&targetId=" + mid);
        assertThat(hasRecord(logs.get("data").get("records"), "action", "RESTORE_MEDIA")).isTrue();
    }

    // ===================== 内容安全覆盖补全 =====================

    @Test
    void Z_内容安全补全_菜谱_食集_点评_家庭名_成员称呼命中禁发词451且不落库() throws Exception {
        String token = loginAdmin();
        JsonNode dish = post(token, "/dish", Map.of("dish", Map.of("name", P + "赌博菜")));
        assertThat(dish.get("code").asInt()).as(dish.toString()).isEqualTo(451);
        JsonNode menu = post(token, "/menu", Map.of("menu", Map.of("name", P + "赌博食集")));
        assertThat(menu.get("code").asInt()).as(menu.toString()).isEqualTo(451);
        JsonNode review = post(token, "/review", Map.of("dishId", dishId, "starRating", 5, "text", P + "这里能赌博"));
        assertThat(review.get("code").asInt()).as(review.toString()).isEqualTo(451);
        JsonNode member = post(token, "/member", Map.of("name", P + "赌博成员"));
        assertThat(member.get("code").asInt()).as(member.toString()).isEqualTo(451);
        JsonNode family = post(token, "/family", Map.of("name", P + "赌博之家"));
        assertThat(family.get("code").asInt()).as(family.toString()).isEqualTo(451);

        // 不落库
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE name = ?", Integer.class, P + "赌博菜")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu WHERE name = ?", Integer.class, P + "赌博食集")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE name = ?", Integer.class, P + "赌博成员")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family WHERE name = ?", Integer.class, P + "赌博之家")).isZero();
        List<String> leftover = new ArrayList<>();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE text = ?", Integer.class, P + "这里能赌博") > 0) {
            leftover.add("review");
        }
        assertThat(leftover).as("违规点评不应落库").isEmpty();
    }
}
