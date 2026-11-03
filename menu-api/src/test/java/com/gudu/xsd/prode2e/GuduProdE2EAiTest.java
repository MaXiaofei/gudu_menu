package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * 生产端到端 · 会调用大模型的用例集中地（咕嘟 Agent、拍照识别、一周餐程、营养补全 / 估算、菜单推荐、AI 对话删除）。
 *
 * <p><b>开关</b>：除总闸外还须设置环境变量 {@code GUDU_PROD_E2E_AI=1}（{@link ProdE2EAiCondition}），否则整类跳过。
 *
 * <p><b>费用</b>：本机进程不配 {@code DEEPSEEK_API_KEY} / {@code GLM_API_KEY} 等密钥时，provider 自动降级为 mock，
 * 不会产生任何真实调用费用，结果确定，全部断言按 mock 编写；配了密钥才会真调模型（产生费用，且模型输出不确定，
 * 依赖 mock 固定结果的断言会被 {@code assumeFalse} 跳过或放宽）。建议只在需要验证真实模型时才配密钥开启。
 * 每个业务点只触发一次模型调用，没有循环重试。
 *
 * <p><b>全站开关</b>：生产里 AI 类全站开关默认状态未知（小程序默认开、App 默认关，且可能被运营关停）。
 * 本类的请求不带 X-Client（按 App 端），所以 {@code @BeforeAll} 先快照 {@code sys_switch}，把用到的四个 App 端开关
 * （咕嘟助手 / 拍照识别 / 一周餐程 / 营养估算）经 {@code PUT /admin/feature-switches} 置为开（reason 以 E2E_PROD_ 开头），
 * {@code @AfterAll}（先于基类清理）用 API 改回原值，再 {@code restoreOrFail()} 兜底校验。选 App 端是因为 App 暂未上线，
 * 打开它不会让真实用户的小程序行为发生变化。
 * 不改 ai_guard_config 额度（用自建新账号，额度不会触顶）；不切换 AI provider、不改网关路由（K 用例只读）。
 */
@ExtendWith(ProdE2EAiCondition.class)
class GuduProdE2EAiTest extends ProdE2EBase {

    private static final List<String> SWITCH_KEYS = List.of(
            "agent.chat.enabled.app", "ai.vision.enabled.app", "ai.mealplan.enabled.app", "ai.estimate.enabled.app");

    private static final String IMG = "/gudu/uploads/original/20261016/e2e-prod-vision.jpg";

    @Autowired
    private com.gudu.xsd.common.upload.UploadUrlSigner uploadSigner;

    private ProdE2ESnapshot switchSnapshot;
    /** 开关的原始生效值（含「无记录取默认」）。 */
    private final Map<String, Boolean> originalSwitches = new LinkedHashMap<>();

    // ================================================================== 开关生命周期

    @BeforeAll
    void enableAiSwitches() {
        switchSnapshot = snapshot("sys_switch", "switch_key");
        String admin = loginAdmin();
        JsonNode list = get(admin, "/admin/feature-switches");
        assertThat(list.get("code").asInt()).as("读开关 msg=" + text(list, "msg")).isEqualTo(0);
        for (JsonNode s : list.get("data")) {
            if (SWITCH_KEYS.contains(text(s, "key"))) originalSwitches.put(text(s, "key"), s.get("enabled").asBoolean());
        }
        assertThat(originalSwitches.keySet()).as("应读到四个 App 端 AI 开关").containsAll(SWITCH_KEYS);
        for (Map.Entry<String, Boolean> e : originalSwitches.entrySet()) {
            if (!e.getValue()) setSwitch(admin, e.getKey(), true, P + "AI端到端测试临时打开");
        }
    }

    @AfterAll
    void restoreAiSwitches() {
        try {
            if (!originalSwitches.isEmpty()) {
                String admin = loginAdmin();
                for (Map.Entry<String, Boolean> e : originalSwitches.entrySet()) {
                    if (!e.getValue()) setSwitch(admin, e.getKey(), false, P + "AI端到端测试还原");
                }
            }
        } finally {
            if (switchSnapshot != null) switchSnapshot.restoreOrFail();
        }
    }

    private void setSwitch(String admin, String key, boolean enabled, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("key", key);
        body.put("enabled", enabled);
        body.put("reason", reason);
        JsonNode r = put(admin, "/admin/feature-switches", body);
        assertThat(r.get("code").asInt()).as("切换开关 " + key + " msg=" + text(r, "msg")).isEqualTo(0);
    }

    /** 本机进程是否配了真实模型密钥（配了会真调，mock 固定结果的断言要放宽）。 */
    private static boolean realModel() {
        return notBlank(System.getenv("DEEPSEEK_API_KEY")) || notBlank(System.getenv("GLM_API_KEY"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ================================================================== 咕嘟 Agent

    @Test
    void 会话_流式对话事件顺序_消息落库_调用链可查() throws Exception {
        String admin = loginAdmin();
        String sid = createSession(admin, P + "冰箱能做啥");
        List<Event> ev = stream(admin, sid, "冰箱里快坏的东西今晚能做啥？");
        assertThat(ev.get(0).name).isEqualTo("meta");
        assertThat(ev.get(ev.size() - 1).name).isEqualTo("done");
        assertThat(ev).extracting(e -> e.name).contains("token");
        assertThat(ev.get(ev.size() - 1).data.get("usage").has("costFen")).isTrue();
        String traceId = ev.get(0).data.get("traceId").asText();
        String messageId = ev.get(0).data.get("messageId").asText();
        if (!realModel()) {
            assertThat(ev).extracting(e -> e.name).contains("tool_start", "tool_end");
        }

        JsonNode msgs = get(admin, "/agent/sessions/" + sid + "/messages?size=10");
        assertThat(msgs.get("code").asInt()).isZero();
        JsonNode items = msgs.get("data").get("items");
        assertThat(items).hasSize(2);
        JsonNode assistant = items.get(0);
        assertThat(assistant.get("messageId").asText()).isEqualTo(messageId);
        if (!realModel()) {
            assertThat(assistant.get("tools").size()).isPositive();
            assertThat(assistant.get("tools").get(0).get("name").asText()).isEqualTo("get_pantry");
        }

        JsonNode sessions = get(admin, "/agent/sessions?size=5");
        assertThat(sessions.get("data").get("items").get(0).get("sessionId").asText()).isEqualTo(sid);

        JsonNode trace = get(admin, "/admin/agent/traces/" + traceId);
        assertThat(trace.get("code").asInt()).as(trace.toString()).isZero();
        assertThat(trace.get("data").get("trace").get("promptVersion").asText())
                .isEqualTo(com.gudu.xsd.modules.agent.application.AgentPrompts.VERSION);
        List<String> spanNames = new ArrayList<>();
        trace.get("data").get("spans").forEach(s -> spanNames.add(s.get("name").asText()));
        if (realModel()) {
            assertThat(spanNames).contains("guard.input", "guard.output");
        } else {
            assertThat(spanNames).contains("guard.input", "llm.plan", "tool.get_pantry", "guard.output");
        }
        assertThat(get(admin, "/admin/agent/traces/" + traceId + "/messages").get("data")).hasSize(2);
        assertThat(get(admin, "/admin/agent/traces?size=5").get("data").get("items").size()).isPositive();
        JsonNode stats = get(admin, "/admin/agent/stats");
        assertThat(stats.get("data").get("turns").asLong()).isPositive();
    }

    @Test
    void 写操作_确认前不落库_他人无权确认_确认生效_重复确认拒绝_撤销恢复() throws Exception {
        assumeFalse(realModel(), "已配真实模型密钥：写操作卡片由模型决定，本用例的固定句式断言基于规则意图解析，跳过");
        String admin = loginAdmin();
        String otherPhone = phoneFor("agentChef");
        createAccount(otherPhone, P + "agent他人");
        String other = loginAs(otherPhone, PW);
        long menuId = createMenu(admin, P + "咕嘟食集");
        String sid = createSession(admin, P + "写操作");

        List<Event> ev = stream(admin, sid, "把" + P + "番茄炒蛋加进" + P + "咕嘟食集里");
        Event action = ev.stream().filter(e -> e.name.equals("action")).findFirst().orElseThrow(
                () -> new AssertionError("应生成待确认动作：" + names(ev)));
        String actionId = action.data.get("actionId").asText();
        assertThat(action.data.get("tool").asText()).isEqualTo("add_to_menu");
        assertThat(action.data.get("status").asText()).isEqualTo("PENDING");
        assertThat(action.data.get("args").get("dishes").get(0).asText()).isEqualTo(P + "番茄炒蛋");
        assertThat(menuDishes(admin, menuId)).as("确认前不落库").isEmpty();

        JsonNode otherTry = post(other, "/agent/actions/" + actionId + "/confirm", null);
        assertThat(otherTry.get("code").asInt()).as("他人不能确认").isNotZero();

        JsonNode ok = post(admin, "/agent/actions/" + actionId + "/confirm", null);
        assertThat(ok.get("code").asInt()).as(ok.toString()).isZero();
        String undo = ok.get("data").get("undoToken").asText();
        assertThat(menuDishes(admin, menuId)).contains(dishId);
        assertThat(post(admin, "/agent/actions/" + actionId + "/confirm", null).get("code").asInt()).as("重复确认").isNotZero();

        JsonNode undone = post(admin, "/agent/actions/undo", Map.of("undoToken", undo));
        assertThat(undone.get("code").asInt()).as(undone.toString()).isZero();
        assertThat(menuDishes(admin, menuId)).as("撤销后恢复").isEmpty();
        JsonNode msgs = get(admin, "/agent/sessions/" + sid + "/messages?size=10");
        assertThat(msgs.get("data").get("items").get(0).get("actions").get(0).get("status").asText()).isEqualTo("UNDONE");
    }

    @Test
    void 修改待确认卡_旧卡被替代_新卡重算预览() throws Exception {
        assumeFalse(realModel(), "已配真实模型密钥：回写卡片由模型决定，本用例的固定句式断言基于规则意图解析，跳过");
        String admin = loginAdmin();
        String sid = createSession(admin, P + "改人份");
        List<Event> ev = stream(admin, sid, P + "番茄炒蛋做完了，我们两个人吃的");
        Event action = ev.stream().filter(e -> e.name.equals("action")).findFirst().orElseThrow(
                () -> new AssertionError("应生成做完回写卡：" + names(ev)));
        String oldId = action.data.get("actionId").asText();
        assertThat(action.data.get("preview").get("servings").asInt()).isEqualTo(2);

        JsonNode revised = post(admin, "/agent/actions/" + oldId + "/revise", Map.of("args", Map.of("servings", 3)));
        assertThat(revised.get("code").asInt()).as(revised.toString()).isZero();
        assertThat(revised.get("data").get("preview").get("servings").asInt()).isEqualTo(3);
        assertThat(revised.get("data").get("actionId").asText()).isNotEqualTo(oldId);
        assertThat(post(admin, "/agent/actions/" + oldId + "/confirm", null).get("code").asInt())
                .as("旧卡已被替代").isNotZero();
        JsonNode cancel = post(admin, "/agent/actions/" + revised.get("data").get("actionId").asText() + "/cancel", null);
        assertThat(cancel.get("code").asInt()).isZero();
    }

    @Test
    void 越权_他人会话不可读_非管理员不能看调用链() {
        String admin = loginAdmin();
        String otherPhone = phoneFor("agentSnoop");
        createAccount(otherPhone, P + "agent越权");
        String other = loginAs(otherPhone, PW);
        String sid = createSession(admin, P + "私有会话");
        assertThat(get(other, "/agent/sessions/" + sid + "/messages").get("code").asInt()).isEqualTo(403);
        assertThat(get(other, "/admin/agent/traces").get("code").asInt()).isEqualTo(403);
        assertThat(get(other, "/admin/agent/eval/runs").get("code").asInt()).isEqualTo(403);
    }

    @Test
    void 差评_调用链标记_回流为评测草稿_评测运行与用例统计() throws Exception {
        String admin = loginAdmin();
        String sid = createSession(admin, P + "差评");
        List<Event> ev = stream(admin, sid, "今晚吃啥");
        String messageId = ev.get(0).data.get("messageId").asText();
        String traceId = ev.get(0).data.get("traceId").asText();
        assertThat(post(admin, "/agent/messages/" + messageId + "/feedback", Map.of("rating", -1, "comment", P + "不好"))
                .get("code").asInt()).isZero();
        JsonNode t = get(admin, "/admin/agent/traces/" + traceId);
        assertThat(t.get("data").get("trace").get("feedback").asInt()).isEqualTo(-1);
        assertThat(t.get("data").get("trace").get("status").asText()).isEqualTo("DOWNVOTED");

        // 草稿用例的 case_id = feedback.<traceId>，from_trace_id 指向自建会话的调用链，基类清理按会话 -> 调用链 -> 用例删除
        JsonNode draft = post(admin, "/admin/agent/eval/cases", Map.of("fromTraceId", traceId));
        assertThat(draft.get("code").asInt()).as(draft.toString()).isZero();
        assertThat(draft.get("data").get("status").asText()).isEqualTo("DRAFT");
        JsonNode cases = get(admin, "/admin/agent/eval/cases");
        assertThat(cases.get("data").get("byIntent")).hasSizeGreaterThanOrEqualTo(14);

        // 评测运行固定用 mock（规则意图，不调真实模型）；版本号带前缀，基类按 prompt_version 前缀清理
        JsonNode run = post(admin, "/admin/agent/eval/runs", Map.of("promptVersion", P + "mock", "provider", "mock",
                "split", "test", "runs", 1));
        assertThat(run.get("code").asInt()).as(run.toString()).isZero();
        long runId = run.get("data").get("runId").asLong();
        String status = "RUNNING";
        for (int i = 0; i < 120 && "RUNNING".equals(status); i++) {
            Thread.sleep(500);
            for (JsonNode r : get(admin, "/admin/agent/eval/runs").get("data")) {
                if (r.get("runId").asLong() == runId) status = r.get("status").asText();
            }
        }
        assertThat(status).isEqualTo("DONE");
        assertThat(get(admin, "/admin/agent/eval/runs/" + runId + "/failures").get("code").asInt()).isZero();
    }

    @Test
    void 首屏建议() {
        String admin = loginAdmin();
        JsonNode s = get(admin, "/agent/suggestions");
        assertThat(s.get("code").asInt()).as(s.toString()).isZero();
        assertThat(s.get("data").get("greeting").asText()).isNotBlank();
        assertThat(s.get("data").get("prompts").size()).isPositive();
    }

    // ================================================================== 拍照识别

    @Test
    void A_冰箱识别_分级与别名对齐_放进余量只能一次() throws Exception {
        assumeFalse(realModel(), "已配真实模型密钥：样图不是真实图片，识别结果断言基于 mock，跳过");
        String token = loginAdmin();
        JsonNode created = post(token, "/vision/jobs", Map.of("mode", "FRIDGE", "imageUrl", img()));
        assertThat(created.get("code").asInt()).as("建任务 msg=" + text(created, "msg")).isEqualTo(0);
        long jobId = created.get("data").get("jobId").asLong();

        JsonNode job = waitDone(token, jobId);
        JsonNode items = job.get("items");
        assertThat(items.size()).as("柠檬 0.31 被丢弃，剩 9 项").isEqualTo(9);
        int confirm = 0;
        boolean tomatoAligned = false;
        for (JsonNode it : items) {
            if ("CONFIRM".equals(it.get("level").asText())) confirm++;
            if ("西红柿".equals(it.get("rawName").asText()) && "番茄".equals(it.get("name").asText())) {
                tomatoAligned = !it.get("ingredientId").isNull();
            }
        }
        assertThat(confirm).isEqualTo(2);
        assertThat(tomatoAligned).as("西红柿 → 番茄 并带食材 id").isTrue();
        assertThat(text(job, "imageExpiresAt")).isNotBlank();

        Map<String, Object> item = new HashMap<>();
        item.put("name", P + "视觉菜心");
        item.put("amount", 1);
        item.put("unit", "把");
        item.put("bestBefore", LocalDate.now().plusDays(5).toString());
        JsonNode applied = post(token, "/vision/jobs/" + jobId + "/apply", Map.of("items", List.of(item)));
        assertThat(applied.get("code").asInt()).as("入库 msg=" + text(applied, "msg")).isEqualTo(0);
        assertThat(applied.get("data").get("added").asInt()).isEqualTo(1);

        JsonNode again = post(token, "/vision/jobs/" + jobId + "/apply", Map.of("items", List.of(item)));
        assertThat(again.get("code").asInt()).as("重复入库应被拒").isNotEqualTo(0);
    }

    @Test
    void B_菜谱截图识别_外链图片被拒() throws Exception {
        String token = loginAdmin();
        // 先做不触发模型的拒绝断言
        JsonNode evil = post(token, "/vision/jobs", Map.of("mode", "FRIDGE", "imageUrl", "https://evil.example/x.jpg"));
        assertThat(evil.get("code").asInt()).as("外链图片应被拒").isNotEqualTo(0);
        JsonNode badMode = post(token, "/vision/jobs", Map.of("mode", "CAMERA", "imageUrl", img()));
        assertThat(badMode.get("code").asInt()).isNotEqualTo(0);

        assumeFalse(realModel(), "已配真实模型密钥：样图不是真实图片，识别结果断言基于 mock，跳过");
        JsonNode created = post(token, "/vision/jobs", Map.of("mode", "RECIPE", "imageUrl", img()));
        assertThat(created.get("code").asInt()).isEqualTo(0);
        JsonNode job = waitDone(token, created.get("data").get("jobId").asLong());
        assertThat(job.get("recipe").get("title").asText()).isEqualTo("滑蛋虾仁");
        assertThat(job.get("recipe").get("steps").size()).isEqualTo(4);
        assertThat(job.get("recipe").get("ingredients").size()).isGreaterThanOrEqualTo(3);
    }

    // ================================================================== 一周餐程

    @Test
    void C_一周餐程_周一开始_换一道_生成食集与采购() {
        String token = loginAdmin();
        long draftId = -1;
        try {
            JsonNode created = post(token, "/mealplan/drafts", Map.of("meals", List.of("LUNCH", "DINNER"),
                    "weekdayMaxMinutes", 60, "preferExpiring", true));
            assertThat(created.get("code").asInt()).as("生成 msg=" + text(created, "msg")).isEqualTo(0);
            JsonNode draft = created.get("data");
            draftId = draft.get("draftId").asLong();
            LocalDate weekStart = LocalDate.parse(draft.get("weekStart").asText());
            assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(LocalDate.parse(draft.get("weekEnd").asText())).isEqualTo(weekStart.plusDays(6));
            assertThat(weekStart).isAfterOrEqualTo(LocalDate.now());
            assertThat(draft.get("days").size()).isEqualTo(7);
            assertThat(draft.get("days").get(0).get("weekday").asText()).isEqualTo("周一");
            assertThat(draft.get("check").get("notes").size()).isGreaterThan(0);
            assertThat(draft.get("constraints").toString()).contains("口人");

            Set<Long> inWeek = new HashSet<>();
            long firstSlot = -1;
            long secondDish = -1;
            for (JsonNode day : draft.get("days")) {
                for (JsonNode slot : day.get("slots")) {
                    assertThat(inWeek.add(slot.get("dish").get("id").asLong())).as("本周不重样").isTrue();
                    assertThat(slot.get("dish").get("coverage")).isNotNull();
                    if (firstSlot < 0) firstSlot = slot.get("slotId").asLong();
                    else if (secondDish < 0) secondDish = slot.get("dish").get("id").asLong();
                }
            }
            assertThat(inWeek).isNotEmpty();

            JsonNode alts = post(token, "/mealplan/drafts/" + draftId + "/slots/" + firstSlot + "/alternatives", null);
            assertThat(alts.get("code").asInt()).isEqualTo(0);
            JsonNode altItems = alts.get("data").get("items");
            assertThat(altItems.size()).isBetween(1, 3);
            for (JsonNode a : altItems) assertThat(inWeek).doesNotContain(a.get("id").asLong());
            long newDish = altItems.get(0).get("id").asLong();

            JsonNode replaced = put(token, "/mealplan/drafts/" + draftId + "/slots/" + firstSlot, Map.of("dishId", newDish));
            assertThat(replaced.get("code").asInt()).as("换一道 msg=" + text(replaced, "msg")).isEqualTo(0);
            assertThat(replaced.toString()).contains("\"id\":" + newDish);
            JsonNode dup = put(token, "/mealplan/drafts/" + draftId + "/slots/" + firstSlot, Map.of("dishId", secondDish));
            assertThat(dup.get("code").asInt()).as("换成本周已有的菜应被拒").isNotEqualTo(0);

            JsonNode applied = post(token, "/mealplan/drafts/" + draftId + "/apply", null);
            assertThat(applied.get("code").asInt()).as("生成食集 msg=" + text(applied, "msg")).isEqualTo(0);
            List<Long> menuIds = new ArrayList<>();
            applied.get("data").get("menuIds").forEach(n -> menuIds.add(n.asLong()));
            assertThat(menuIds).hasSize(7);
            assertThat(applied.get("data").get("shoppingItemCount").asInt()).isGreaterThanOrEqualTo(0);

            JsonNode menu = get(token, "/menu/" + menuIds.get(0));
            assertThat(menu.get("code").asInt()).isEqualTo(0);
            assertThat(menu.toString()).contains("一周餐程");

            JsonNode after = get(token, "/mealplan/drafts/" + draftId);
            assertThat(after.get("data").get("status").asText()).isEqualTo("APPLIED");
            JsonNode again = post(token, "/mealplan/drafts/" + draftId + "/apply", null);
            assertThat(again.get("code").asInt()).as("重复生成应被拒").isNotEqualTo(0);

            for (Long id : menuIds) delete(token, "/menu/" + id); // 自清理（超管家庭由基类兜底）
        } finally {
            // meal_plan_slot 只有 draft_id、没有家庭 / 个人归属列，基类清理认不出：按本用例创建的草稿 id 精确删除
            if (draftId > 0) jdbc.update("DELETE FROM meal_plan_slot WHERE draft_id = ?", draftId);
        }
    }

    @Test
    void D_指定周必须是周一_营养报告() {
        String token = loginAdmin();
        LocalDate notMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.TUESDAY));
        JsonNode bad = post(token, "/mealplan/drafts", Map.of("weekStart", notMonday.toString()));
        assertThat(bad.get("code").asInt()).as("非周一应被拒").isNotEqualTo(0);

        long memberId = get(token, "/member/me").get("data").get("id").asLong();
        JsonNode report = get(token, "/nutrition/report?memberId=" + memberId + "&range=week");
        assertThat(report.get("code").asInt()).as("报告 msg=" + text(report, "msg")).isEqualTo(0);
        JsonNode data = report.get("data");
        assertThat(data.get("radar").size()).isEqualTo(5);
        assertThat(LocalDate.parse(data.get("range").get("from").asText()).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(data.get("trend").get("key").asText()).isEqualTo("sodium");
        assertThat(data.get("member").get("memberId").asLong()).isEqualTo(memberId);

        JsonNode month = get(token, "/nutrition/report?memberId=" + memberId + "&range=month");
        assertThat(month.get("code").asInt()).isEqualTo(0);
        assertThat(LocalDate.parse(month.get("data").get("range").get("from").asText()).getDayOfMonth()).isEqualTo(1);
        JsonNode badRange = get(token, "/nutrition/report?memberId=" + memberId + "&range=year");
        assertThat(badRange.get("code").asInt()).isNotEqualTo(0);
    }

    // ================================================================== 营养补全 / 菜单推荐 / 估算 / 对话删除

    @Test
    void AI_营养补全番茄_六项指标落库() {
        String token = loginAdmin();
        // 营养补全会整体覆盖食材的营养：生产里的公共食材「番茄」不能动，改在自建私有食材上做（请求里的 name 仍用「番茄」）
        Map<String, Object> ing = new HashMap<>();
        ing.put("name", P + "补全番茄");
        ing.put("edible", 1);
        JsonNode created = post(token, "/ingredient", Map.of("ingredient", ing, "nutritions", List.of()));
        assertThat(created.get("code").asInt()).as("建食材 msg=" + text(created, "msg")).isEqualTo(0);
        long ingId = created.get("data").asLong();
        try {
            JsonNode r = post(token, "/ai/nutrition/fill", Map.of("name", "番茄", "ingredientId", ingId));
            assertThat(r.get("code").asInt()).as("营养补全应成功 msg=" + text(r, "msg")).isEqualTo(0);
            JsonNode data = r.get("data");
            assertThat(data.get("nutrition").isArray()).isTrue();
            if (!realModel()) {
                assertThat(data.get("source").asText()).isEqualTo("mock");
                assertThat(data.get("nutrition").size()).isEqualTo(6);
                int cal = -1;
                for (JsonNode n : data.get("nutrition")) {
                    if (n.get("metricId").asLong() == 1L) {
                        cal = n.get("value").asInt();
                        break;
                    }
                }
                assertThat(cal).as("番茄 calorie 应=19").isEqualTo(19);
            } else {
                assertThat(data.get("nutrition").size()).isPositive();
            }

            JsonNode nut = get(token, "/ingredient/" + ingId + "/nutrition");
            assertThat(nut.get("code").asInt()).isEqualTo(0);
            JsonNode map = nut.get("data");
            if (!realModel()) {
                String[] metrics = {"1:calorie", "2:protein", "3:fat", "4:carb", "5:sugar", "6:gi"};
                for (String m : metrics) {
                    String[] kv = m.split(":");
                    assertThat(map.has(kv[0])).as("落库应含 " + kv[1]).isTrue();
                }
            } else {
                assertThat(map.size()).as("落库应有营养项").isPositive();
            }
        } finally {
            delete(token, "/ingredient/" + ingId); // 基类也会按前缀兜底
        }
    }

    @Test
    void AI_菜单推荐_DAY候选受限() {
        String token = loginAdmin();
        // 生产测试进程里向量库是空实现，推荐召回为空列表；断言「候选至多 1 组、有候选时菜非空」
        Map<String, Object> req = new HashMap<>();
        req.put("memberId", chefMemberId);
        req.put("scope", "DAY");
        JsonNode r = post(token, "/ai/menu/recommend", req);
        assertThat(r.get("code").asInt()).as("菜单推荐应成功 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode arr = r.get("data");
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).as("DAY 至多 1 组候选").isLessThanOrEqualTo(1);
        for (JsonNode g : arr) {
            assertThat(g.get("source").asText()).isNotBlank();
            assertThat(g.get("dishes").isArray()).isTrue();
            assertThat(g.get("dishes").size()).isPositive();
        }
    }

    @Test
    void K_AI估算_调用日志用量_provider查询() {
        String token = loginAdmin();

        JsonNode est = post(token, "/ai/dish/estimate",
                Map.of("description", "一盘番茄炒蛋约两人份", "servingFactor", 1));
        assertThat(est.get("code").asInt()).as("AI 估算 msg=" + text(est, "msg")).isEqualTo(0);
        assertThat(est.get("data").get("nutrition").size()).isPositive();

        // 调用日志是全站表，只做「包含本次估算」的下界断言（只读）
        JsonNode callLog = get(token, "/ai/call-log?scene=dish_estimate&pageNum=1&pageSize=5");
        assertThat(callLog.get("code").asInt()).isEqualTo(0);
        assertThat(callLog.get("data").get("total").asLong()).isPositive();

        JsonNode usage = get(token, "/ai/usage?days=7");
        assertThat(usage.get("code").asInt()).isEqualTo(0);

        // provider 与网关只读查询。生产里不调 PUT /ai/provider：它改写的是全站 CHAT/TOOLS 网关路由
        JsonNode provider = get(token, "/ai/provider");
        assertThat(provider.get("code").asInt()).isEqualTo(0);
        assertThat(provider.get("data").get("current").asText()).isNotBlank();
        JsonNode gateway = get(token, "/admin/ai/gateway");
        assertThat(gateway.get("code").asInt()).as("网关只读 msg=" + text(gateway, "msg")).isEqualTo(0);
    }

    @Test
    void Z_AI对话可自行删除_删后消息404_清空接口可用() {
        String token = loginAdmin();
        JsonNode ses = post(token, "/agent/sessions", Map.of("title", P + "待删除会话"));
        assertThat(ses.get("code").asInt()).as(ses.toString()).isZero();
        String sessionId = ses.get("data").get("sessionId").asText();

        JsonNode del = delete(token, "/agent/sessions/" + sessionId);
        assertThat(del.get("code").asInt()).as(del.toString()).isZero();
        // 已物理删除：再取消息 / 再删都是 404（与「别人的会话」同一响应，不暴露存在性）
        assertThat(get(token, "/agent/sessions/" + sessionId + "/messages").get("code").asInt()).isEqualTo(404);
        assertThat(delete(token, "/agent/sessions/" + sessionId).get("code").asInt()).isEqualTo(404);

        // 「清空」只作用于调用者本账号的全部会话（此处是 E2E 超管账号），不影响他人
        JsonNode clear = delete(token, "/agent/sessions");
        assertThat(clear.get("code").asInt()).as(clear.toString()).isZero();
        assertThat(clear.get("data").has("deleted")).isTrue();
    }

    // ================================================================== 工具

    private String img() {
        return uploadSigner.sign(IMG);
    }

    private JsonNode waitDone(String token, long jobId) throws InterruptedException {
        JsonNode last = null;
        for (int i = 0; i < 200; i++) {
            last = get(token, "/vision/jobs/" + jobId);
            assertThat(last.get("code").asInt()).isEqualTo(0);
            if (!"PENDING".equals(last.get("data").get("status").asText())) break;
            Thread.sleep(300);
        }
        assertThat(last.get("data").get("status").asText()).as("识别应完成").isEqualTo("DONE");
        return last.get("data");
    }

    private record Event(String name, JsonNode data) {
    }

    private static List<String> names(List<Event> ev) {
        return ev.stream().map(e -> e.name + (e.name.equals("tool_end") ? "(" + e.data.get("summary").asText() + ")" : ""))
                .toList();
    }

    private List<Event> stream(String token, String sessionId, String text) throws Exception {
        String body = exchangeRaw(token, "/agent/sessions/" + sessionId + "/messages", HttpMethod.POST,
                om.writeValueAsString(Map.of("text", text)), Map.of("Accept", "text/event-stream")).getBody();
        assertThat(body).as("SSE 响应为空").isNotBlank();
        List<Event> out = new ArrayList<>();
        for (String block : body.split("\\r?\\n\\r?\\n")) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\\r?\\n")) {
                if (line.startsWith("event:")) name = line.substring(6).trim();
                else if (line.startsWith("data:")) data.append(line.substring(5));
            }
            if (name != null) out.add(new Event(name, om.readTree(data.toString())));
        }
        return out;
    }

    private String createSession(String token, String title) {
        JsonNode r = post(token, "/agent/sessions", Map.of("title", title));
        assertThat(r.get("code").asInt()).as(r.toString()).isZero();
        return r.get("data").get("sessionId").asText();
    }

    private long createMenu(String token, String name) {
        JsonNode r = post(token, "/menu", Map.of("menu", Map.of("name", name, "servingCount", 2, "status", "ACTIVE"),
                "dishes", List.of()));
        assertThat(r.get("code").asInt()).as(r.toString()).isZero();
        for (JsonNode m : get(token, "/menu?pageNum=1&pageSize=50").get("data").get("records")) {
            if (name.equals(m.get("name").asText())) return m.get("id").asLong();
        }
        throw new AssertionError("新建食集未找到");
    }

    private List<Long> menuDishes(String token, long menuId) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode d : get(token, "/menu/" + menuId).get("data").get("dishes")) ids.add(d.get("dishId").asLong());
        return ids;
    }
}
