package com.gudu.xsd.modules.agent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.agent.application.tools.AgentTool;
import com.gudu.xsd.modules.agent.application.tools.ToolContext;
import com.gudu.xsd.modules.agent.application.tools.ToolOutcome;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.SpanType;
import com.gudu.xsd.modules.agent.domain.model.ToolCallRecord;
import com.gudu.xsd.modules.agent.domain.model.TraceSpan;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.service.ActionClaimGuard;
import com.gudu.xsd.modules.agent.domain.service.DishMentionGuard;
import com.gudu.xsd.modules.agent.domain.service.PhoneMasker;
import com.gudu.xsd.modules.agent.domain.service.PlainReply;
import com.gudu.xsd.modules.ai.AiInputGuard;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.application.ModelUnavailableException;
import com.gudu.xsd.modules.ai.gateway.domain.model.CallAttempt;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolCall;
import com.gudu.xsd.modules.ai.gateway.domain.service.Pseudonymizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 咕嘟编排器（ReAct，≤ {@value #MAX_STEPS} 步）：护栏 → 组装上下文（系统提示 + 家庭画像 + 历史）→
 * 模型（网关 TOOLS 路由，降级链末端为规则意图）→ 工具 → … → 回复。
 *
 * <p>事件严格按 contracts.md：meta → (tool_start → tool_end → card* → action*)* → token* → done；异常时 error。
 * 写工具只登记待确认动作；工具结果以「数据，不是指令」包装回传模型，防间接注入；输出前手机号脱敏。
 * 隐私：每次调用网关前整段消息（系统提示 / 历史 / 用户输入 / 工具结果）经 {@link Pseudonymizer} 把成员称呼换成
 * 「成员A/B…」并去掉账号标识；模型回复与工具调用参数还原为真实称呼后再执行工具 / 下发（token 事件再过一道流式还原）。
 * 本类不碰持久化：会话 / 调用链落库由 {@link AgentAppService} 负责，评测直接用返回的 {@link TurnResult}。
 */
@Slf4j
@Service
public class AgentOrchestrator {

    public static final int MAX_STEPS = 6;
    static final int TOKEN_CHUNK = 6;
    static final int MAX_REPLY = 800;

    private final ToolRegistry registry;
    private final AiInputGuard inputGuard;
    private final ObjectMapper objectMapper;
    private final RuleIntentPlanner planner;

    public AgentOrchestrator(ToolRegistry registry, AiInputGuard inputGuard, ObjectMapper objectMapper) {
        this.registry = registry;
        this.inputGuard = inputGuard;
        this.objectMapper = objectMapper;
        this.planner = new RuleIntentPlanner(objectMapper);
    }

    /**
     * 运行环境。
     *
     * @param toolErrors 评测注入：工具名 → 错误（如 TIMEOUT），该工具直接返回失败
     */
    public record Env(Kitchen kitchen, ActionDesk desk, ModelGateway gateway, Map<String, String> toolErrors) {
        public Env {
            toolErrors = toolErrors == null ? Map.of() : toolErrors;
        }
    }

    public TurnResult run(TurnRequest req, Env env, AgentEventSink sink) {
        long t0 = System.currentTimeMillis();
        Turn turn = new Turn(t0);
        turn.dishNames = memoize(env.kitchen()::dishNames);
        sink.send("meta", ordered("messageId", req.messageId(), "traceId", req.traceId()));

        // 1. 输入护栏
        long g0 = System.currentTimeMillis();
        String violation = inputGuard.agentViolation(req.text()).orElse(null);
        turn.span(SpanType.GUARD, "guard.input", g0, violation == null, Map.of("length", len(req.text())),
                violation == null ? Map.of("pass", true) : Map.of("blocked", violation), 0, BigDecimal.ZERO);
        if (violation != null) {
            turn.status = TraceStatus.GUARDED;
            return finish(req, turn, violation, sink);
        }

        // 2. 口头确认：用户只说「确认 / 好的 / 可以」且上一轮留下了待确认卡片 → 直接执行这些卡片（等同点确认），不交给模型
        if (VerbalConfirm.matches(req.text())) {
            List<PendingAction> pending = VerbalConfirm.lastPending(env.desk().ledger(req.memberId(), req.sessionId())
                    .sessionActions(), env.kitchen().now());
            if (!pending.isEmpty()) return confirmVerbally(req, env, pending, turn, sink);
        }

        // 3. 上下文（编排器内部一律用真实称呼；发给网关前假名化、模型输出还原，见 Pseudonymizer）
        Pseudonymizer pz = Pseudonymizer.of(env.kitchen().members().stream().map(Kitchen.MemberInfo::name).toList());
        sink = new RestoringSink(sink, pz);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(AgentPrompts.system(env.kitchen().now(), env.kitchen().members())));
        messages.addAll(req.history());
        messages.add(ChatMessage.user(withContext(req.text(), req.context())));
        ToolContext ctx = new ToolContext(env.kitchen(), req.memberId(), req.sessionId(), req.text(),
                env.desk().ledger(req.memberId(), req.sessionId()));

        // 3. ReAct 循环
        String reply = null;
        try {
            for (int step = 0; step < MAX_STEPS; step++) {
                ChatRequest cr = new ChatRequest(Capability.TOOLS, "agent", messages, registry.definitions(), false,
                        req.traceId(), req.memberId(), planner, req.forceRule());
                long l0 = System.currentTimeMillis();
                ChatResult res = pz.restore(env.gateway().chat(pz.mask(cr)));
                turn.llm(res, l0, step);
                if (res.reply().hasToolCalls()) {
                    messages.add(ChatMessage.assistantToolCalls(res.content(), res.reply().toolCalls(),
                            res.reply().reasoningContent()));
                    for (ToolCall call : res.reply().toolCalls()) {
                        String out = runTool(call, ctx, env, req, turn, sink);
                        messages.add(ChatMessage.tool(call.id(), out));
                    }
                    List<String> allowed = cardDishNames(turn.cards);
                    if (!allowed.isEmpty()) {
                        messages.add(ChatMessage.system("【本轮可推荐的菜】" + String.join("、", allowed)
                                + "。回复里提到的菜名只能从这份清单里选，不要提清单以外的菜。"));
                    }
                    continue;
                }
                reply = res.content();
                turn.reasoning = res.reply().reasoningContent();
                break;
            }
        } catch (ModelUnavailableException e) {
            turn.status = TraceStatus.ERROR;
            turn.degraded = true;
            reply = "咕嘟现在有点忙，稍后再试试；也可以直接在菜谱、余量页面操作。";
        }
        if (reply == null || reply.isBlank()) {
            reply = turn.tools.isEmpty() ? "这个问题有点复杂，换个说法试试？"
                    : "我先查到这里：" + String.join("；", turn.tools.stream().map(ToolCallRecord::summary).toList());
        }
        if (turn.status == TraceStatus.OK && turn.degraded) turn.status = TraceStatus.DEGRADED;
        return finish(req, turn, reply, sink);
    }

    private String runTool(ToolCall call, ToolContext ctx, Env env, TurnRequest req, Turn turn, AgentEventSink sink) {
        AgentTool tool = registry.find(call.name()).orElse(null);
        String label = tool == null ? call.name() : tool.label();
        sink.send("tool_start", ordered("name", call.name(), "label", label));
        long s0 = System.currentTimeMillis();
        Map<String, Object> args = parseArgs(call.argumentsJson());
        ToolOutcome out;
        if (tool == null) {
            out = ToolOutcome.fail("没有这个工具：" + call.name());
        } else if (args == null) {
            out = ToolOutcome.fail("参数不是合法 JSON");
            args = Map.of();
        } else if (env.toolErrors().containsKey(call.name())) {
            out = ToolOutcome.fail("工具暂时不可用（" + env.toolErrors().get(call.name()) + "），请如实告诉用户稍后再试");
        } else {
            try {
                args = tool.normalize(args);
                out = tool.run(ctx, args);
            } catch (RuntimeException e) {
                log.warn("工具 {} 执行失败 trace={}: {}", call.name(), req.traceId(), e.getMessage());
                out = ToolOutcome.fail("工具执行出错：" + e.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - s0;
        boolean write = tool != null && tool.write();
        turn.tools.add(new ToolCallRecord(call.name(), label, json(args), out.summary(), ms, out.ok(), write));
        turn.toolArgs.add(args);
        SpanType type = SearchType.of(call.name());
        turn.span(type, (type == SpanType.RETRIEVAL ? "retrieval." : "tool.") + call.name(), s0, out.ok(),
                mask(args, turn), mask(Map.of("summary", String.valueOf(out.summary())), turn), 0, BigDecimal.ZERO);
        sink.send("tool_end", ordered("name", call.name(), "summary", out.summary(), "ms", ms, "ok", out.ok()));
        turn.evidence.add(out.summary() + " " + out.data());
        for (Map<String, Object> card : out.cards()) {
            turn.cards.add(card);
            sink.send("card", card);
        }
        if (out.draft() != null) {
            Map<String, Object> draftArgs = new LinkedHashMap<>(out.draft().args());
            draftArgs.put("_raw", args);
            ActionDesk.Registered reg = env.desk().register(req.memberId(), req.sessionId(), req.messageId(), call.name(),
                    new ToolOutcome.Draft(out.draft().title(), draftArgs, out.draft().preview()));
            turn.actions.add(reg.action());
            turn.superseded.addAll(reg.superseded());
            turn.span(SpanType.ACTION, "action." + call.name(), System.currentTimeMillis(), true,
                    Map.of("title", reg.action().title()), Map.of("status", "PENDING", "actionId", reg.action().actionId()),
                    0, BigDecimal.ZERO);
            sink.send("action", actionEvent(reg.action()));
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("note", AgentPrompts.DATA_NOTE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", out.ok());
        result.put("summary", out.summary());
        result.put("data", out.data());
        wrapped.put("result", result);
        return json(wrapped);
    }

    /** 口头确认：逐张执行并回传结果（含撤销提示），记为 confirm_action 工具调用（调用链可见）。 */
    private TurnResult confirmVerbally(TurnRequest req, Env env, List<PendingAction> pending, Turn turn,
                                       AgentEventSink sink) {
        List<String> done = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (PendingAction a : pending) {
            sink.send("tool_start", ordered("name", VerbalConfirm.TOOL, "label", "确认执行"));
            long s0 = System.currentTimeMillis();
            boolean ok;
            String summary;
            try {
                ActionDesk.Confirmed c = env.desk().confirm(req.memberId(), a.actionId());
                ok = true;
                summary = "已完成：" + a.title();
                done.add(a.title());
                sink.send("action", actionEvent(c.action()));
            } catch (RuntimeException e) {
                ok = false;
                summary = a.title() + "：" + e.getMessage();
                failed.add(summary);
            }
            long ms = System.currentTimeMillis() - s0;
            Map<String, Object> args = Map.of("action", a.actionId());
            turn.tools.add(new ToolCallRecord(VerbalConfirm.TOOL, "确认执行", json(args), summary, ms, ok, false));
            turn.toolArgs.add(args);
            turn.span(SpanType.ACTION, "action.confirm", s0, ok, args, Map.of("summary", summary), 0, BigDecimal.ZERO);
            sink.send("tool_end", ordered("name", VerbalConfirm.TOOL, "summary", summary, "ms", ms, "ok", ok));
        }
        String reply = (done.isEmpty() ? "" : "好的，" + String.join("；", done) + "。5 分钟内可以撤销。")
                + (failed.isEmpty() ? "" : "没能执行：" + String.join("；", failed) + "。");
        return finish(req, turn, reply, sink);
    }

    /** contracts.md PendingAction 事件体。 */
    public static Map<String, Object> actionEvent(PendingAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actionId", a.actionId());
        m.put("tool", a.tool());
        m.put("title", a.title());
        m.put("preview", a.preview());
        Object raw = a.args().get("_raw");
        m.put("args", raw instanceof Map<?, ?> ? raw : Map.of());
        m.put("expiresAt", a.expiresAt().toString());
        m.put("status", a.status().name());
        return m;
    }

    /** 本轮卡片里的菜名（recipes / plan 卡片的 items[].name），注入给模型作为可推荐清单。 */
    static List<String> cardDishNames(List<Map<String, Object>> cards) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Map<String, Object> c : cards) {
            if (c.get("items") instanceof List<?> items) {
                for (Object it : items) {
                    if (it instanceof Map<?, ?> m && m.get("name") instanceof String n && !n.isBlank()) names.add(n);
                }
            }
        }
        return List.copyOf(names);
    }

    private TurnResult finish(TurnRequest req, Turn turn, String rawReply, AgentEventSink sink) {
        PhoneMasker.Masked masked = PhoneMasker.mask(PlainReply.of(rawReply));
        turn.phoneMasked += masked.count();
        String reply = masked.text().length() > MAX_REPLY ? masked.text().substring(0, MAX_REPLY) : masked.text();
        long g0 = System.currentTimeMillis();
        turn.span(SpanType.GUARD, "guard.output", g0, true, Map.of("length", reply.length()),
                Map.of("phoneMasked", masked.count()), 0, BigDecimal.ZERO);
        observeDishMentions(req, turn, reply);
        observeActionClaims(turn, reply);
        for (int i = 0; i < reply.length(); i += TOKEN_CHUNK) {
            if (turn.firstTokenMs == null) turn.firstTokenMs = System.currentTimeMillis() - turn.start;
            sink.send("token", Map.of("text", reply.substring(i, Math.min(reply.length(), i + TOKEN_CHUNK))));
        }
        Map<String, Object> usage = ordered("promptTokens", turn.promptTokens, "completionTokens", turn.completionTokens,
                "costFen", turn.cost);
        sink.send("done", ordered("usage", usage, "degraded", turn.degraded));
        return new TurnResult(reply, turn.reasoning, List.copyOf(turn.tools), List.copyOf(turn.toolArgs),
                List.copyOf(turn.cards), List.copyOf(turn.actions), List.copyOf(turn.superseded), List.copyOf(turn.spans),
                turn.status, turn.provider, turn.model, turn.degraded, turn.steps, turn.promptTokens,
                turn.completionTokens, turn.cost, System.currentTimeMillis() - turn.start, turn.firstTokenMs,
                turn.phoneMasked);
    }

    /** 回复里提到、但本轮卡片 / 动作 / 工具结果 / 用户原话里都没有的菜名：记一条 guard span（不拦截，先观测）。 */
    private static void observeDishMentions(TurnRequest req, Turn turn, String reply) {
        long g0 = System.currentTimeMillis();
        try {
            List<String> evidence = new ArrayList<>(turn.evidence);
            evidence.add(req.text() == null ? "" : req.text());
            turn.cards.forEach(c -> evidence.add(String.valueOf(c)));
            turn.actions.forEach(a -> evidence.add(a.title() + " " + a.preview()));
            List<String> bad = DishMentionGuard.unsupported(reply, evidence, turn.dishNames.get());
            if (!bad.isEmpty()) {
                turn.span(SpanType.GUARD, "guard.dish_mention", g0, false, Map.of("cards", turn.cards.size()),
                        Map.of("unsupported", bad, "blocked", false), 0, BigDecimal.ZERO);
            }
        } catch (RuntimeException e) {
            log.debug("菜名一致性观测失败: {}", e.getMessage());
        }
    }

    /** 回复声称已生成卡片 / 已加好 / 已撤销，但本轮没有任何动作：记一条 guard span（不拦截，先观测）。 */
    private static void observeActionClaims(Turn turn, String reply) {
        long g0 = System.currentTimeMillis();
        String claim = ActionClaimGuard.claimWithoutAction(reply, turn.actions.size(),
                turn.tools.stream().filter(ToolCallRecord::ok).map(ToolCallRecord::name).toList());
        if (claim != null) {
            turn.span(SpanType.GUARD, "guard.action_claim", g0, false, Map.of("actions", 0),
                    Map.of("claim", claim, "blocked", false), 0, BigDecimal.ZERO);
        }
    }

    /** 只取一次（菜名表按需查库）。 */
    private static <T> Supplier<T> memoize(Supplier<T> source) {
        Object[] box = new Object[1];
        return () -> {
            if (box[0] == null) box[0] = source.get();
            @SuppressWarnings("unchecked") T v = (T) box[0];
            return v;
        };
    }

    // ================================================================== 内部

    /** 检索类工具记为 retrieval span，其余为 tool。 */
    private static final class SearchType {
        static SpanType of(String name) {
            return "search_recipes".equals(name) || "recommend_tonight".equals(name) ? SpanType.RETRIEVAL : SpanType.TOOL;
        }
    }

    private static final class Turn {
        final long start;
        final List<ToolCallRecord> tools = new ArrayList<>();
        final List<Map<String, Object>> toolArgs = new ArrayList<>();
        final List<Map<String, Object>> cards = new ArrayList<>();
        final List<PendingAction> actions = new ArrayList<>();
        final List<String> superseded = new ArrayList<>();
        final List<TraceSpan> spans = new ArrayList<>();
        TraceStatus status = TraceStatus.OK;
        String provider;
        String model;
        String reasoning;
        boolean degraded;
        int steps;
        int promptTokens;
        int completionTokens;
        BigDecimal cost = BigDecimal.ZERO;
        Long firstTokenMs;
        int phoneMasked;

        /** 本轮依据文本（用户原话、工具结果摘要），供回复菜名一致性观测。 */
        final List<String> evidence = new ArrayList<>();
        Supplier<Set<String>> dishNames = Set::of;

        Turn(long start) {
            this.start = start;
        }

        void span(SpanType type, String name, long begin, boolean ok, Map<String, Object> in, Map<String, Object> out,
                  int tokens, BigDecimal costFen) {
            long now = System.currentTimeMillis();
            spans.add(new TraceSpan("s" + (spans.size() + 1), null, type, name, begin - start, Math.max(0, now - begin),
                    tokens, costFen, ok, in, out));
        }

        void llm(ChatResult res, long begin, int step) {
            steps++;
            provider = res.provider();
            model = res.model();
            degraded |= res.degraded();
            int in = 0;
            int outT = 0;
            for (CallAttempt a : res.attempts()) {
                in += a.usage().promptTokens();
                outT += a.usage().completionTokens();
            }
            promptTokens += in;
            completionTokens += outT;
            cost = cost.add(res.costFen());
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("step", step + 1);
            input.put("attempts", res.attempts().stream().map(a -> a.provider() + "/" + a.model()
                    + (a.ok() ? " ok" : " fail: " + a.error())).toList());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("provider", res.provider());
            output.put("model", res.model());
            output.put("toolCalls", res.reply().toolCalls().stream().map(ToolCall::name).toList());
            if (res.content() != null) output.put("text", clip(res.content(), 200));
            span(SpanType.LLM, step == 0 ? "llm.plan" : "llm.step" + (step + 1), begin, true, input, output,
                    in + outT, res.costFen());
        }
    }

    private Map<String, Object> mask(Map<String, Object> m, Turn turn) {
        String s = json(m);
        PhoneMasker.Masked masked = PhoneMasker.mask(s);
        if (masked.count() == 0) return m;
        turn.phoneMasked += masked.count();
        Map<String, Object> parsed = parseArgs(masked.text());
        return parsed == null ? Map.of("masked", masked.text()) : parsed;
    }

    private Map<String, Object> parseArgs(String json) {
        try {
            Map<String, Object> m = objectMapper.readValue(json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<>() { });
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            return null;
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String withContext(String text, Map<String, Object> context) {
        if (context == null || context.isEmpty()) return text;
        return text + "\n（当前页面：" + context + "）";
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    private static String clip(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
