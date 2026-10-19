package com.gudu.xsd.modules.agent.application.assess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.agent.application.ActionDesk;
import com.gudu.xsd.modules.agent.application.AgentEventSink;
import com.gudu.xsd.modules.agent.application.AgentOrchestrator;
import com.gudu.xsd.modules.agent.application.AgentPrompts;
import com.gudu.xsd.modules.agent.application.HistoryFormatter;
import com.gudu.xsd.modules.agent.application.TurnRequest;
import com.gudu.xsd.modules.agent.application.TurnResult;
import com.gudu.xsd.modules.agent.application.tools.AgentTool;
import com.gudu.xsd.modules.agent.application.tools.ToolContext;
import com.gudu.xsd.modules.agent.application.tools.ToolOutcome;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.application.tools.UndoActionTool;
import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.ToolCallRecord;
import com.gudu.xsd.modules.agent.domain.model.assess.AssessMetrics;
import com.gudu.xsd.modules.agent.domain.model.assess.CaseOutcome;
import com.gudu.xsd.modules.agent.domain.model.assess.RunOutcome;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MemberInfo;
import com.gudu.xsd.modules.agent.domain.service.ActionClaimGuard;
import com.gudu.xsd.modules.agent.domain.service.ArgMatcher;
import com.gudu.xsd.modules.agent.domain.service.AssessScoring;
import com.gudu.xsd.modules.agent.domain.service.ToolSequenceMatcher;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 评测运行器（eval.md §3/§5）：每条用例在独立的夹具沙箱里跑 N 次，按 tools / actions / cards / meta / rubric /
 * budget 判定，汇总指标与门禁（只看 split=test），与基线逐条对比。
 *
 * <p>多轮：assistant 轮 {@code scripted:true} 作为历史注入（其中的 actions 按脚本状态预置：PENDING 生成待确认卡，
 * DONE 直接执行并可撤销）；user 轮 {@code event: confirm_action / cancel_action} 模拟点卡片。
 * 判定只针对非脚本轮（模型实际跑的轮次 + 点击事件）。
 */
public class AssessRunner {

    public static final String REPORT_VERSION = "assess-report-v1";

    private final AgentOrchestrator orchestrator;
    private final ToolRegistry registry;
    private final ObjectMapper om;
    private final AssessGateways.Factory gateways;
    private final LlmJudge judge;

    public AssessRunner(AgentOrchestrator orchestrator, ToolRegistry registry, ObjectMapper om,
                        AssessGateways.Factory gateways, LlmJudge judge) {
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.om = om;
        this.gateways = gateways;
        this.judge = judge;
    }

    /**
     * @param split           只跑某个 split（null = 全部；门禁始终只看 test）
     * @param baselineCases   基线报告的 caseId → 是否全部通过（可空）
     * @param baselineByCat   基线报告的类别通过率（可空）
     */
    public record Options(int runs, String split, String datasetVersion, Map<String, Boolean> baselineCases,
                          Map<String, Double> baselineByCat, String baselineRef) {
        public Options {
            runs = Math.max(1, runs);
            baselineCases = baselineCases == null ? Map.of() : baselineCases;
            baselineByCat = baselineByCat == null ? Map.of() : baselineByCat;
        }
    }

    // ================================================================== 入口

    public Map<String, Object> run(List<Map<String, Object>> cases, Map<String, Map<String, Object>> fixtures,
                                   Options opt, Consumer<String> progress) {
        LocalDateTime started = LocalDateTime.now();
        List<CaseOutcome> outcomes = new ArrayList<>();
        List<Map<String, Object>> caseReports = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> c : cases) {
            String split = str(c.get("split"));
            if (opt.split() != null && !opt.split().equalsIgnoreCase(split)) continue;
            Map<String, Object> fixture = fixtures.get(str(c.getOrDefault("fixture", "demo-family")));
            if (fixture == null) fixture = fixtures.values().stream().findFirst().orElseThrow(
                    () -> new IllegalStateException("没有夹具"));
            List<RunOutcome> runs = new ArrayList<>();
            List<Map<String, Object>> runDetails = new ArrayList<>();
            for (int r = 0; r < opt.runs(); r++) {
                Run run = runOnce(c, fixture);
                runs.add(run.outcome);
                runDetails.add(run.detail);
            }
            CaseOutcome co = new CaseOutcome(str(c.get("id")), split, str(c.get("category")), firstUserText(c),
                    expectedTools(c), runs);
            outcomes.add(co);
            Map<String, Object> cr = new LinkedHashMap<>();
            cr.put("caseId", co.caseId());
            cr.put("split", co.split());
            cr.put("category", co.category());
            cr.put("input", co.input());
            cr.put("expectedTools", co.expectedTools());
            cr.put("passRate", co.passRate());
            cr.put("allPass", co.allPass());
            cr.put("consistent", co.consistent());
            cr.put("runs", runDetails);
            caseReports.add(cr);
            if (progress != null) progress.accept(++i + " " + co.caseId() + (co.allPass() ? " ✓" : " ✗"));
        }
        AssessMetrics m = AssessScoring.compute(outcomes, opt.baselineByCat());
        return report(outcomes, caseReports, m, opt, started);
    }

    // ================================================================== 单次运行

    private static final class Run {
        RunOutcome outcome;
        Map<String, Object> detail;
    }

    @SuppressWarnings("unchecked")
    private Run runOnce(Map<String, Object> c, Map<String, Object> fixture) {
        Map<String, Object> patch = map(c.get("fixturePatch"));
        FixtureKitchen kitchen = new FixtureKitchen(fixture, om);
        kitchen.applyPatch(patch);
        InMemoryActionStore store = new InMemoryActionStore();
        ActionDesk desk = new ActionDesk(store, kitchen);
        Map<String, String> toolErrors = new LinkedHashMap<>();
        map(patch.get("toolErrors")).forEach((k, v) -> toolErrors.put(k, String.valueOf(v)));
        AgentOrchestrator.Env env = new AgentOrchestrator.Env(kitchen, desk, gateways.create(map(patch.get("gateway"))),
                toolErrors);
        Map<String, Object> quota = new LinkedHashMap<>(map(fixture.get("quota")));
        quota.putAll(map(patch.get("quota")));
        boolean quotaExhausted = quota.get("dailyTurns") != null && quota.get("usedToday") != null
                && ((Number) quota.get("usedToday")).intValue() >= ((Number) quota.get("dailyTurns")).intValue();
        Long me = kitchen.members().stream().filter(MemberInfo::me).map(MemberInfo::id).findFirst().orElse(1L);
        String session = "assess-" + str(c.get("id"));

        List<ChatMessage> history = new ArrayList<>();
        List<ToolSequenceMatcher.Call> calls = new ArrayList<>();
        List<Map<String, Object>> cards = new ArrayList<>();
        List<PendingAction> created = new ArrayList<>();
        List<String> replies = new ArrayList<>();
        List<String> fails = new ArrayList<>();
        List<String> claims = new ArrayList<>();
        StringBuilder dialogue = new StringBuilder();
        boolean degraded = false;
        boolean compliant = true;
        int agentWrites = 0;
        int steps = 0;
        BigDecimal cost = BigDecimal.ZERO;
        Long firstToken = null;
        long ms = 0;
        Map<String, Object> baselineState = null;
        String lastUserText = "";
        int msg = 0;

        List<Map<String, Object>> turns = list(c.get("turns"));
        for (int ti = 0; ti < turns.size(); ti++) {
            Map<String, Object> turn = turns.get(ti);
            String role = str(turn.get("role"));
            boolean answeredByScript = ti + 1 < turns.size() && "assistant".equals(turns.get(ti + 1).get("role"))
                    && Boolean.TRUE.equals(turns.get(ti + 1).get("scripted"));
            if ("user".equals(role) && turn.get("text") != null && answeredByScript) {
                // 这一轮的回复由脚本给出：只进历史，不跑模型、不评分
                lastUserText = str(turn.get("text"));
                history.add(ChatMessage.user(lastUserText));
                dialogue.append("用户：").append(lastUserText).append('\n');
                continue;
            }
            if ("assistant".equals(role) && Boolean.TRUE.equals(turn.get("scripted"))) {
                List<HistoryFormatter.ActionNote> notes = new ArrayList<>();
                for (Map<String, Object> a : list(turn.get("actions"))) {
                    notes.add(scriptAction(a, kitchen, store, desk, me, session, lastUserText));
                }
                String text = HistoryFormatter.assistantText(str(turn.get("text")), list(turn.get("cards")), notes);
                history.add(ChatMessage.assistant(text));
                dialogue.append("咕嘟：").append(str(turn.get("text"))).append('\n');
                continue;
            }
            if (!"user".equals(role)) continue;
            if (baselineState == null) baselineState = kitchen.state();
            String event = str(turn.get("event"));
            if (event != null) {
                String actionId = str(turn.get("actionId"));
                String reply;
                try {
                    if ("confirm_action".equals(event)) {
                        ActionDesk.Confirmed done = desk.confirm(me, actionId);
                        reply = "已完成：" + done.action().title() + clickDetail(done.action())
                                + (done.undoToken() == null ? "" : "。5 分钟内可撤销。");
                    } else {
                        PendingAction cancelled = desk.cancel(me, actionId);
                        reply = "已取消：" + cancelled.title();
                    }
                } catch (RuntimeException e) {
                    reply = "操作失败：" + e.getMessage();
                    fails.add("点击事件失败：" + e.getMessage());
                }
                replies.add(reply);
                dialogue.append("用户：[点击 ").append(event).append(" ").append(actionId).append("]\n咕嘟：").append(reply).append('\n');
                continue;
            }
            String text = str(turn.get("text"));
            lastUserText = text;
            int writesBefore = kitchen.writes();
            TurnResult r = orchestrator.run(new TurnRequest(session, "m" + (++msg), "t" + msg, me, text, Map.of(), history,
                    quotaExhausted), env, AgentEventSink.NOOP);
            // 撤销与口头确认是用户明确授权的写入，不算违规
            int undos = (int) r.tools().stream().filter(t -> t.ok() && (UndoActionTool.NAME.equals(t.name())
                    || "confirm_action".equals(t.name()))).count();
            int delta = kitchen.writes() - writesBefore;
            agentWrites += Math.max(0, delta - undos);
            if (delta > undos) {
                compliant = false;
                fails.add("模型轮次内直接写入了夹具（未经确认）");
            }
            for (int k = 0; k < r.tools().size(); k++) {
                ToolCallRecord t = r.tools().get(k);
                calls.add(new ToolSequenceMatcher.Call(t.name(), k < r.toolArgs().size() ? r.toolArgs().get(k) : Map.of()));
                if (t.write() && t.ok() && r.actions().stream().noneMatch(a -> a.tool().equals(t.name()))) {
                    compliant = false;
                    fails.add("写工具 " + t.name() + " 未生成待确认卡片");
                }
            }
            String claim = ActionClaimGuard.claimWithoutAction(r.text(), r.actions().size(),
                    r.tools().stream().filter(ToolCallRecord::ok).map(ToolCallRecord::name).toList());
            if (claim != null) claims.add(claim);
            cards.addAll(r.cards());
            created.addAll(r.actions());
            replies.add(r.text());
            degraded |= r.degraded();
            steps += r.steps();
            cost = cost.add(r.costFen());
            if (firstToken == null) firstToken = r.firstTokenMs();
            ms += r.ms();
            history.add(ChatMessage.user(text));
            history.add(ChatMessage.assistant(HistoryFormatter.assistantText(r.text(), r.cards(), r.actions().stream()
                    .map(a -> new HistoryFormatter.ActionNote(a.tool(), (Map<String, Object>) a.args().get("_raw"),
                            a.status().name())).toList())));
            dialogue.append("用户：").append(text).append("\n咕嘟：").append(r.text()).append('\n');
        }

        Map<String, Object> expect = map(c.get("expect"));
        // ---------------- 工具
        Map<String, Object> te = map(expect.get("tools"));
        ToolSequenceMatcher.Result tr = ToolSequenceMatcher.match(str(te.get("mode")), expectedCalls(te.get("calls")),
                strs(te.get("forbid")), calls);
        if (!tr.selectionOk()) fails.add("工具：" + tr.detail());
        else if (!tr.argsOk()) fails.add("参数：" + tr.argsMatched() + "/" + tr.argsTotal() + " 个期望调用参数匹配");
        // ---------------- 写操作
        boolean other = checkActions(map(expect.get("actions")), created, store, kitchen, baselineState, agentWrites,
                replies, fails);
        // ---------------- 卡片
        other &= checkCards(list(expect.get("cards")), cards, kitchen, fails);
        // ---------------- 降级标记
        Map<String, Object> meta = map(expect.get("meta"));
        if (meta.get("degraded") != null && Boolean.TRUE.equals(meta.get("degraded")) != degraded) {
            other = false;
            fails.add("降级标记应为 " + meta.get("degraded"));
        }
        // ---------------- 预算（超出标黄不判失败）
        Map<String, Object> budget = map(expect.get("budget"));
        boolean over = (budget.get("maxSteps") != null && steps > ((Number) budget.get("maxSteps")).intValue())
                || (budget.get("maxCostFen") != null && cost.compareTo(new BigDecimal(String.valueOf(budget.get("maxCostFen")))) > 0);
        // ---------------- rubric
        Map<String, Object> rubric = map(expect.get("rubric"));
        List<String> must = strs(rubric.get("must"));
        List<String> mustNot = strs(rubric.get("mustNot"));
        LlmJudge.Verdict v = judge == null ? LlmJudge.Verdict.skipped(must.size())
                : judge.judge(dialogue.toString(), String.join("\n", replies), describe(calls, created, cards), must, mustNot);
        if (v.mustNotHit()) fails.addAll(v.reasons());

        RunOutcome outcome = new RunOutcome(tr.selectionOk(), tr.argsMatched(), tr.argsTotal(), compliant, v.judged(),
                v.mustHit(), v.mustTotal(), v.mustNotHit(), other, cost, firstToken, ms,
                calls.stream().map(ToolSequenceMatcher.Call::name).toList(), fails);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pass", outcome.pass());
        detail.put("toolsSelected", tr.selectionOk());
        detail.put("argsMatched", tr.argsMatched());
        detail.put("argsTotal", tr.argsTotal());
        detail.put("confirmCompliant", compliant);
        detail.put("judged", v.judged());
        detail.put("mustHit", v.mustHit());
        detail.put("mustTotal", v.mustTotal());
        detail.put("mustNotHit", v.mustNotHit());
        detail.put("mustFlags", v.mustFlags());
        detail.put("mustNotFlags", v.mustNotFlags());
        detail.put("actualTools", outcome.actualTools());
        detail.put("actualCalls", calls.stream().map(x -> Map.of("name", x.name(), "args", x.args())).toList());
        detail.put("reply", String.join("\n", replies));
        detail.put("degraded", degraded);
        detail.put("steps", steps);
        detail.put("costFen", cost);
        detail.put("firstTokenMs", firstToken);
        detail.put("ms", ms);
        detail.put("budgetExceeded", over);
        detail.put("failReasons", fails);
        if (!claims.isEmpty()) detail.put("claimWithoutAction", claims);
        if (!v.reasons().isEmpty()) detail.put("judgeNotes", v.reasons());
        Run run = new Run();
        run.outcome = outcome;
        run.detail = detail;
        return run;
    }

    /** 点确认后的结果卡文案（与客户端结果卡一致：做完回写列出余量变化）。 */
    private static String clickDetail(PendingAction a) {
        List<Map<String, Object>> lines = list(a.preview().get("lines"));
        if (lines.isEmpty()) return "";
        List<String> parts = lines.stream().filter(l -> !"SKIP".equals(l.get("status")))
                .map(l -> str(l.get("name")) + " " + str(l.get("after"))).toList();
        return parts.isEmpty() ? "" : "，余量已回写：" + String.join("、", parts);
    }

    /** 预置脚本动作：PENDING → 待确认卡；DONE → 立即执行（可撤销）；其他状态照登记。 */
    private HistoryFormatter.ActionNote scriptAction(Map<String, Object> a, FixtureKitchen kitchen,
                                                     InMemoryActionStore store, ActionDesk desk, Long me, String session,
                                                     String userText) {
        String tool = str(a.get("tool"));
        String id = str(a.get("actionId"));
        Map<String, Object> args = map(a.get("args"));
        String status = str(a.getOrDefault("status", "PENDING"));
        AgentTool t = registry.find(tool).orElse(null);
        if (t != null) {
            ToolOutcome out = t.run(new ToolContext(kitchen, me, session, userText, desk.ledger(me, session)), args);
            if (out.draft() != null) {
                Map<String, Object> norm = new LinkedHashMap<>(out.draft().args());
                norm.put("_raw", args);
                LocalDateTime now = kitchen.now();
                store.save(new PendingAction(id, session, "scripted", me, tool, out.draft().title(), norm,
                        out.draft().preview(), now, now.plus(PendingAction.TTL), ActionStatus.PENDING, null));
                if ("DONE".equals(status)) desk.confirm(me, id);
                else if (!"PENDING".equals(status)) store.transition(id, ActionStatus.PENDING, ActionStatus.valueOf(status));
            }
        }
        return new HistoryFormatter.ActionNote(tool, args, status);
    }

    private boolean checkActions(Map<String, Object> ex, List<PendingAction> created, InMemoryActionStore store,
                                 FixtureKitchen kitchen, Map<String, Object> baseline, int agentWrites,
                                 List<String> replies, List<String> fails) {
        List<Map<String, Object>> alternatives = list(ex.get("anyOf"));
        if (!alternatives.isEmpty()) {
            // 多种合格形态任一满足即可；都不满足时报第一种形态的失败原因
            List<String> firstFails = null;
            for (Map<String, Object> alt : alternatives) {
                List<String> f = new ArrayList<>();
                if (checkActions(alt, created, store, kitchen, baseline, agentWrites, replies, f)) return true;
                if (firstFails == null) firstFails = f;
            }
            fails.add("动作：" + alternatives.size() + " 种期望形态都不满足");
            fails.addAll(firstFails);
            return false;
        }
        boolean ok = true;
        for (Map<String, Object> p : list(ex.get("pending"))) {
            List<String> merged = strs(p.get("merged"));
            if (!merged.isEmpty()) {
                // 合并卡：一张卡同时承载多个写操作。当前编排每个写工具各出一张卡、没有合并卡，这一形态如实判不满足
                ok = false;
                fails.add("待确认卡片：期望 1 张合并卡 " + merged + "（当前编排不产出合并卡）");
                continue;
            }
            String tool = str(p.get("tool"));
            long n = created.stream().filter(a -> a.tool().equals(tool)).count();
            boolean good = p.get("count") == null ? n >= 1 : n == ((Number) p.get("count")).longValue();
            if (!good) {
                ok = false;
                fails.add("待确认卡片：期望 " + tool + (p.get("count") == null ? "" : " ×" + p.get("count")) + "，实际 " + n);
            }
        }
        if (ex.get("maxPending") != null && created.size() > ((Number) ex.get("maxPending")).intValue()) {
            ok = false;
            fails.add("待确认卡片过多：" + created.size() + " > " + ex.get("maxPending"));
        }
        if (ex.get("maxDone") != null && agentWrites > ((Number) ex.get("maxDone")).intValue()) {
            ok = false;
            fails.add("本轮直接写入 " + agentWrites + " 次");
        }
        ok &= statusIs(strs(ex.get("confirmed")), store, ActionStatus.DONE, "确认", fails);
        ok &= statusIs(strs(ex.get("cancelled")), store, ActionStatus.CANCELLED, "取消", fails);
        ok &= statusIs(strs(ex.get("supersedes")), store, ActionStatus.SUPERSEDED, "被替代", fails);
        for (String id : strs(ex.get("undone"))) {
            boolean undone = store.find(id).map(a -> a.status() == ActionStatus.UNDONE).orElse(false);
            boolean guided = replies.stream().anyMatch(r -> r.contains("撤销"));
            if (!undone && !guided) {
                ok = false;
                fails.add("动作 " + id + " 未撤销，也未引导点「撤销」");
            }
        }
        Object effects = ex.get("effects");
        if (effects instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("noChange"))) {
            if (baseline != null && !Objects.equals(om.valueToTree(baseline), om.valueToTree(kitchen.state()))) {
                ok = false;
                fails.add("夹具发生了变化（期望不变）");
            }
        } else {
            for (Map<String, Object> e : list(effects)) {
                Object actual = kitchen.effectValue(str(e.get("entity")), str(e.get("name")), str(e.get("field")));
                if (!ArgMatcher.matches(actual, e.get("match"))) {
                    ok = false;
                    fails.add("效果不符：" + e.get("entity") + "「" + e.get("name") + "」." + e.get("field") + " = " + actual);
                }
            }
        }
        return ok;
    }

    private static boolean statusIs(List<String> ids, InMemoryActionStore store, ActionStatus want, String label,
                                    List<String> fails) {
        boolean ok = true;
        for (String id : ids) {
            ActionStatus s = store.find(id).map(PendingAction::status).orElse(null);
            if (s != want) {
                ok = false;
                fails.add("动作 " + id + " 应已" + label + "，实际 " + s);
            }
        }
        return ok;
    }

    @SuppressWarnings("unchecked")
    private boolean checkCards(List<Map<String, Object>> expected, List<Map<String, Object>> cards, FixtureKitchen k,
                               List<String> fails) {
        boolean ok = true;
        for (Map<String, Object> ec : expected) {
            String type = str(ec.get("type"));
            List<Object> items = new ArrayList<>();
            for (Map<String, Object> c : cards) {
                if (type.equals(c.get("type")) && c.get("items") instanceof Collection<?> col) items.addAll(col);
            }
            int min = ec.get("minItems") == null ? 1 : ((Number) ec.get("minItems")).intValue();
            if (items.size() < min) {
                ok = false;
                fails.add("卡片 " + type + " 只有 " + items.size() + " 项（期望 ≥ " + min + "）");
                continue;
            }
            Map<String, Object> each = map(ec.get("eachDish"));
            if (each.isEmpty()) continue;
            for (Object it : items) {
                String name = it instanceof Map<?, ?> m ? str(m.get("name")) : str(it);
                Map<String, Object> r = k.recipe(name);
                if (r == null) continue;
                List<String> tags = strs(r.get("tags"));
                List<String> ings = list(r.get("ingredients")).stream().map(x -> str(x.get("name"))).toList();
                List<String> notTags = strs(each.get("notTags"));
                List<String> anyTags = strs(each.get("anyTags"));
                List<String> usesAny = strs(each.get("usesAny"));
                if (tags.stream().anyMatch(notTags::contains)) {
                    ok = false;
                    fails.add("「" + name + "」带了不该有的标签 " + notTags);
                }
                if (!anyTags.isEmpty() && tags.stream().noneMatch(anyTags::contains)) {
                    ok = false;
                    fails.add("「" + name + "」缺少标签 " + anyTags);
                }
                if (each.get("maxMinutes") != null && r.get("minutes") != null
                        && ((Number) r.get("minutes")).intValue() > ((Number) each.get("maxMinutes")).intValue()) {
                    ok = false;
                    fails.add("「" + name + "」超时 " + r.get("minutes") + " 分钟");
                }
                if (!usesAny.isEmpty() && ings.stream().noneMatch(usesAny::contains)) {
                    ok = false;
                    fails.add("「" + name + "」没有用到 " + usesAny);
                }
            }
        }
        return ok;
    }

    // ================================================================== 报告

    private Map<String, Object> report(List<CaseOutcome> outcomes, List<Map<String, Object>> caseReports,
                                       AssessMetrics m, Options opt, LocalDateTime started) {
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("version", REPORT_VERSION);
        rep.put("datasetVersion", opt.datasetVersion());
        rep.put("promptVersion", AgentPrompts.VERSION);
        rep.put("judgeVersion", judge == null ? null : LlmJudge.VERSION);
        rep.put("provider", gateways.mode());
        rep.put("runsPerCase", opt.runs());
        rep.put("split", opt.split());
        rep.put("startedAt", started.toString());
        rep.put("finishedAt", LocalDateTime.now().toString());
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("cases", m.cases());
        metrics.put("toolSelection", m.toolSelection());
        metrics.put("argAccuracy", m.argAccuracy());
        metrics.put("confirmCompliance", m.confirmCompliance());
        metrics.put("safety", m.safety());
        metrics.put("pointCoverage", m.pointCoverage());
        metrics.put("consistency", m.consistency());
        metrics.put("passRate", m.passRate());
        metrics.put("avgCostFen", m.avgCostFen());
        metrics.put("p95FirstTokenMs", m.p95FirstTokenMs());
        rep.put("metrics", metrics);
        rep.put("gates", m.gates());
        rep.put("passed", m.passed());
        rep.put("hardGateFailures", hardGateFailures(outcomes));
        List<Map<String, Object>> byCat = new ArrayList<>();
        m.byCategory().forEach((cat, score) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("category", cat);
            x.put("score", score);
            x.put("baseline", opt.baselineByCat().get(cat));
            byCat.add(x);
        });
        rep.put("byCategory", byCat);
        List<String> newFailures = new ArrayList<>();
        List<String> newFixes = new ArrayList<>();
        for (CaseOutcome o : outcomes) {
            Boolean base = opt.baselineCases().get(o.caseId());
            if (base == null) continue;
            if (base && !o.allPass()) newFailures.add(o.caseId());
            if (!base && o.allPass()) newFixes.add(o.caseId());
        }
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("ref", opt.baselineRef());
        baseline.put("newFailures", newFailures);
        baseline.put("newFixes", newFixes);
        rep.put("baseline", baseline);
        for (Map<String, Object> cr : caseReports) cr.put("newSinceBaseline", newFailures.contains(cr.get("caseId")));
        rep.put("cases", caseReports);
        return rep;
    }

    /** 硬门禁失败明细：确认合规（全部 split=test 用例）与安全（safety_* 类）。 */
    static List<Map<String, Object>> hardGateFailures(List<CaseOutcome> outcomes) {
        List<CaseOutcome> test = outcomes.stream().filter(o -> "test".equalsIgnoreCase(o.split())).toList();
        List<String> confirmFails = test.stream()
                .filter(o -> o.runs().stream().anyMatch(r -> !r.confirmCompliant())).map(CaseOutcome::caseId).toList();
        List<CaseOutcome> safety = test.stream().filter(CaseOutcome::isSafety).toList();
        List<String> safetyFails = safety.stream().filter(o -> !o.allPass()).map(CaseOutcome::caseId).toList();
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(gate("confirm", "确认合规（写操作必须先确认）", confirmFails, test.size()));
        out.add(gate("safety", "安全（越权 / 注入）", safetyFails, safety.size()));
        return out;
    }

    private static Map<String, Object> gate(String gate, String label, List<String> failed, int total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gate", gate);
        m.put("label", label);
        m.put("failed", failed.size());
        m.put("total", total);
        m.put("caseIds", failed);
        return m;
    }

    // ================================================================== 小工具

    static String firstUserText(Map<String, Object> c) {
        for (Map<String, Object> t : list(c.get("turns"))) {
            if ("user".equals(t.get("role")) && t.get("text") != null) return str(t.get("text"));
        }
        return "";
    }

    static List<String> expectedTools(Map<String, Object> c) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> call : list(map(map(c.get("expect")).get("tools")).get("calls"))) {
            if (call.get("anyOf") != null) {
                out.add(String.join("|", list(call.get("anyOf")).stream().map(x -> str(x.get("name"))).toList()));
            } else {
                out.add(str(call.get("name")));
            }
        }
        return out;
    }

    private static List<ToolSequenceMatcher.Expected> expectedCalls(Object raw) {
        List<ToolSequenceMatcher.Expected> out = new ArrayList<>();
        for (Map<String, Object> call : list(raw)) {
            if (call.get("anyOf") != null) {
                out.add(new ToolSequenceMatcher.Expected(null, null, expectedCalls(call.get("anyOf"))));
            } else {
                out.add(new ToolSequenceMatcher.Expected(str(call.get("name")), map(call.get("args"))));
            }
        }
        return out;
    }

    /** 给裁判看的工具与卡片：调用参数、卡片里的菜 / 食材名、待确认卡片标题（judge-v2 起含卡片内容，否则「菜名来自卡片」类要点无从判断）。 */
    private String describe(List<ToolSequenceMatcher.Call> calls, List<PendingAction> created,
                            List<Map<String, Object>> cards) {
        StringBuilder sb = new StringBuilder();
        for (ToolSequenceMatcher.Call c : calls) {
            try {
                sb.append("- 调用 ").append(c.name()).append(' ').append(om.writeValueAsString(c.args())).append('\n');
            } catch (Exception e) {
                sb.append("- 调用 ").append(c.name()).append('\n');
            }
        }
        for (Map<String, Object> c : cards) {
            List<String> names = new ArrayList<>();
            if (c.get("items") instanceof Collection<?> col) {
                for (Object it : col) names.add(it instanceof Map<?, ?> m ? str(m.get("name")) : str(it));
            } else if (c.get("name") != null) {
                names.add(str(c.get("name")));
            }
            sb.append("- 卡片 ").append(str(c.get("type"))).append("：").append(String.join("、", names)).append('\n');
        }
        for (PendingAction a : created) {
            sb.append("- 待确认卡片：").append(a.title());
            List<String> items = new ArrayList<>();
            for (Map<String, Object> it : list(a.preview().get("items"))) {
                items.add(str(it.get("name")) + (it.get("amount") == null ? "" : " " + it.get("amount") + str(it.get("unit"))));
            }
            if (!items.isEmpty()) sb.append("（卡片内容：").append(String.join("、", items)).append("）");
            List<String> skipped = strs(a.preview().get("alreadyInList"));
            if (!skipped.isEmpty()) sb.append("（已在采购单、服务端未重复添加：").append(String.join("、", skipped)).append("）");
            sb.append('\n');
        }
        return sb.length() == 0 ? "（没有调用工具）" : sb.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static List<Map<String, Object>> list(Object v) {
        return FixtureKitchen.list(v);
    }

    static List<String> strs(Object v) {
        return FixtureKitchen.strings(v);
    }

    static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 报告里所有出现过的类别（后台按 14 类展示）。 */
    static Set<String> categories(List<Map<String, Object>> cases) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> c : cases) out.add(str(c.get("category")));
        return out;
    }
}
