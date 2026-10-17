package com.gudu.xsd.modules.agent.application.assess;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.agent.application.AgentOrchestrator;
import com.gudu.xsd.modules.agent.application.AgentPrompts;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.domain.model.AgentTrace;
import com.gudu.xsd.modules.agent.domain.model.SpanType;
import com.gudu.xsd.modules.agent.domain.model.TraceSpan;
import com.gudu.xsd.modules.agent.domain.model.assess.StoredCase;
import com.gudu.xsd.modules.agent.domain.model.assess.StoredRun;
import com.gudu.xsd.modules.agent.domain.repository.AgentAssessRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.domain.port.EnvKeyLookup;
import com.gudu.xsd.modules.ai.gateway.domain.port.ModelTransport;
import com.gudu.xsd.modules.ai.gateway.domain.port.SecretCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 后台评测：用例（classpath 数据集 + 库内导入 / 差评回流）、发起运行（后台线程）、运行列表、失败明细、设基线。
 */
@Slf4j
@Service
public class AssessAppService {

    /** eval.md §4 的 14 个类别（后台按此顺序展示）。 */
    public static final List<String> INTENTS = List.of("recipe_search", "pantry_query", "write_menu", "write_shopping",
            "write_cooked", "write_pantry", "multi_step", "profile_constraint", "clarify", "out_of_scope",
            "safety_injection", "safety_tenant", "robustness", "degradation");

    private final AgentAssessRepository repo;
    private final AgentTraceRepository traces;
    private final AgentOrchestrator orchestrator;
    private final ToolRegistry registry;
    private final ModelGateway gateway;
    private final ModelTransport transport;
    private final SecretCipher cipher;
    private final EnvKeyLookup envKeys;
    private final ObjectMapper om;
    private final Executor executor;

    public AssessAppService(AgentAssessRepository repo, AgentTraceRepository traces, AgentOrchestrator orchestrator,
                            ToolRegistry registry, ModelGateway gateway, ModelTransport transport, SecretCipher cipher,
                            EnvKeyLookup envKeys, ObjectMapper om, @Qualifier("agentExecutor") Executor executor) {
        this.repo = repo;
        this.traces = traces;
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.gateway = gateway;
        this.transport = transport;
        this.cipher = cipher;
        this.envKeys = envKeys;
        this.om = om;
        this.executor = executor;
    }

    // ================================================================== 用例

    public Map<String, Object> cases(String intent) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> c : AssessDataset.load(om).cases()) items.add(caseItem(c, "dataset", "ACTIVE"));
        for (StoredCase s : repo.listCases()) {
            Map<String, Object> c = parse(s.json());
            items.add(caseItem(c, s.source(), s.status()));
        }
        Map<String, int[]> counts = new LinkedHashMap<>();
        INTENTS.forEach(i -> counts.put(i, new int[2]));
        for (Map<String, Object> it : items) {
            int[] c = counts.computeIfAbsent(String.valueOf(it.get("intent")), k -> new int[2]);
            c[0]++;
            if ("test".equals(it.get("split"))) c[1]++;
        }
        List<Map<String, Object>> byIntent = new ArrayList<>();
        counts.forEach((k, v) -> byIntent.add(Map.of("intent", k, "count", v[0], "testCount", v[1])));
        List<Map<String, Object>> filtered = intent == null || intent.isBlank() ? items
                : items.stream().filter(i -> intent.equals(i.get("intent"))).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", items.size());
        out.put("byIntent", byIntent);
        out.put("items", filtered);
        return out;
    }

    private static Map<String, Object> caseItem(Map<String, Object> c, String source, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseId", c.get("id"));
        m.put("split", c.get("split"));
        m.put("intent", c.get("category"));
        m.put("input", AssessRunner.firstUserText(c));
        m.put("expectedTools", AssessRunner.expectedTools(c));
        m.put("difficulty", c.get("difficulty"));
        m.put("source", source);
        m.put("status", status);
        return m;
    }

    /**
     * 新增用例：body.case 为一行用例 JSON（导入，ACTIVE）；或 body.fromTraceId 由差评 / 拦截对话生成草稿（DRAFT，
     * 带原始输入与该轮实际工具调用，人工补期望后改为 ACTIVE）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createCase(Map<String, Object> body) {
        if (body != null && body.get("fromTraceId") != null) {
            String traceId = String.valueOf(body.get("fromTraceId"));
            AgentTrace t = traces.find(traceId).orElseThrow(() -> new BizException(404, "调用链不存在"));
            List<String> tools = traces.spans(traceId).stream()
                    .filter(s -> s.type() == SpanType.TOOL || s.type() == SpanType.RETRIEVAL)
                    .map(TraceSpan::name).map(n -> n.replaceFirst("^(tool|retrieval)\\.", "")).toList();
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("id", "feedback." + traceId);
            draft.put("split", "dev");
            draft.put("category", body.getOrDefault("category", "recipe_search"));
            draft.put("tags", List.of("feedback"));
            draft.put("difficulty", "medium");
            draft.put("fixture", "demo-family");
            draft.put("turns", List.of(Map.of("role", "user", "text", t.firstText() == null ? "" : t.firstText())));
            draft.put("fixturePatch", null);
            draft.put("expect", Map.of("tools", Map.of("mode", "subsequence",
                    "calls", tools.stream().map(n -> Map.of("name", n, "args", Map.of())).toList(), "forbid", List.of()),
                    "actions", Map.of(), "rubric", Map.of("must", List.of(), "mustNot", List.of())));
            draft.put("source", "feedback");
            draft.put("notes", "差评回流草稿：实际工具调用仅供参考，需人工标注期望并改写为夹具中的等价数据");
            StoredCase s = repo.saveCase(new StoredCase(null, String.valueOf(draft.get("id")), "dev",
                    String.valueOf(draft.get("category")), write(draft), "feedback", traceId, "DRAFT", null));
            return Map.of("caseId", s.caseId(), "status", s.status());
        }
        Object raw = body == null ? null : body.get("case");
        Map<String, Object> c = raw instanceof Map<?, ?> m ? (Map<String, Object>) m : raw == null ? null : parse(String.valueOf(raw));
        if (c == null || c.get("id") == null || c.get("category") == null || !(c.get("turns") instanceof List<?>)) {
            throw new BizException("用例需包含 id、category、turns");
        }
        String split = c.get("split") == null ? "dev" : String.valueOf(c.get("split"));
        StoredCase s = repo.saveCase(new StoredCase(null, String.valueOf(c.get("id")), split,
                String.valueOf(c.get("category")), write(c), "imported", null, "ACTIVE", null));
        return Map.of("caseId", s.caseId(), "status", s.status());
    }

    // ================================================================== 运行

    /**
     * 发起评测（后台线程）。provider：mock（规则意图，无外部调用）/ gateway（线上网关当前配置，真实模型 + LLM 裁判）。
     */
    public Map<String, Object> startRun(String promptVersion, String provider, String split, Integer runs) {
        String mode = "gateway".equalsIgnoreCase(provider) ? "gateway" : "mock";
        StoredRun r = repo.saveRun(new StoredRun(null, promptVersion == null ? AgentPrompts.VERSION : promptVersion, mode,
                "RUNNING", null, null, null, null, null, null, null, false, null, null, null, null));
        executor.execute(() -> execute(r, split, runs == null ? 3 : runs));
        return Map.of("runId", r.id());
    }

    void execute(StoredRun r, String split, int runs) {
        try {
            AssessDataset ds = AssessDataset.load(om);
            List<Map<String, Object>> cases = new ArrayList<>(ds.cases());
            for (StoredCase s : repo.listCases()) if ("ACTIVE".equals(s.status())) cases.add(parse(s.json()));
            AssessGateways.Factory f = "gateway".equals(r.provider())
                    ? AssessGateways.live(gateway.providers().values(), gateway.routes().values(), transport, cipher, envKeys)
                    : AssessGateways.simulated();
            LlmJudge judge = "gateway".equals(r.provider()) ? new LlmJudge(gateway, om) : null;
            Map<String, Boolean> baseCases = new LinkedHashMap<>();
            Map<String, Double> baseCats = new LinkedHashMap<>();
            String baseRef = null;
            StoredRun base = repo.baseline().orElse(null);
            if (base != null && base.reportJson() != null) {
                Map<String, Object> rep = parse(base.reportJson());
                baseRef = "run:" + base.id();
                for (Map<String, Object> c : AssessRunner.list(rep.get("cases"))) {
                    baseCases.put(String.valueOf(c.get("caseId")), Boolean.TRUE.equals(c.get("allPass")));
                }
                for (Map<String, Object> c : AssessRunner.list(rep.get("byCategory"))) {
                    baseCats.put(String.valueOf(c.get("category")), ((Number) c.get("score")).doubleValue());
                }
            }
            AssessRunner runner = new AssessRunner(orchestrator, registry, om, f, judge);
            Map<String, Object> rep = runner.run(cases, ds.fixtures(),
                    new AssessRunner.Options(runs, split, ds.version(), baseCases, baseCats, baseRef), null);
            rep.put("runId", r.id());
            Map<String, Object> m = AssessRunner.map(rep.get("metrics"));
            int hard = AssessRunner.list(rep.get("hardGateFailures")).stream()
                    .mapToInt(g -> ((Number) g.get("failed")).intValue()).sum();
            repo.saveRun(new StoredRun(r.id(), r.promptVersion(), r.provider(), "DONE", dbl(m.get("toolSelection")),
                    dbl(m.get("argAccuracy")), dbl(m.get("pointCoverage")), (BigDecimal) m.get("avgCostFen"),
                    ((Number) m.get("p95FirstTokenMs")).longValue(), Boolean.TRUE.equals(rep.get("passed")), hard,
                    false, write(rep), null, r.createdAt(), LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.error("评测运行失败 run={}", r.id(), e);
            repo.saveRun(new StoredRun(r.id(), r.promptVersion(), r.provider(), "FAILED", null, null, null, null, null,
                    false, null, false, null, e.getMessage(), r.createdAt(), LocalDateTime.now()));
        }
    }

    public List<Map<String, Object>> runs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StoredRun r : repo.listRuns(20)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runId", r.id());
            m.put("promptVersion", r.promptVersion());
            m.put("provider", r.provider());
            m.put("status", r.status());
            m.put("toolAccuracy", r.toolAccuracy());
            m.put("argAccuracy", r.argAccuracy());
            m.put("pointCoverage", r.pointCoverage());
            m.put("avgCostFen", r.avgCostFen());
            m.put("p95Ms", r.p95Ms());
            m.put("passed", r.passed());
            m.put("baseline", r.baseline());
            m.put("error", r.error());
            m.put("createdAt", r.createdAt());
            m.put("finishedAt", r.finishedAt());
            Map<String, Object> rep = r.reportJson() == null ? Map.of() : parse(r.reportJson());
            m.put("byCategory", rep.getOrDefault("byCategory", List.of()));
            m.put("hardGateFailures", rep.getOrDefault("hardGateFailures", List.of()));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> failures(Long runId) {
        StoredRun r = repo.findRun(runId).orElseThrow(() -> new BizException(404, "评测运行不存在"));
        if (r.reportJson() == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : AssessRunner.list(parse(r.reportJson()).get("cases"))) {
            if (Boolean.TRUE.equals(c.get("allPass"))) continue;
            List<Map<String, Object>> runs = AssessRunner.list(c.get("runs"));
            Map<String, Object> firstFail = runs.stream().filter(x -> !Boolean.TRUE.equals(x.get("pass"))).findFirst()
                    .orElse(Map.of());
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("caseId", c.get("caseId"));
            f.put("intent", c.get("category"));
            f.put("input", c.get("input"));
            f.put("expectedTools", c.get("expectedTools"));
            f.put("actualTools", firstFail.getOrDefault("actualTools", List.of()));
            f.put("note", String.join("；", AssessRunner.strs(firstFail.get("failReasons"))));
            f.put("newSinceBaseline", Boolean.TRUE.equals(c.get("newSinceBaseline")));
            out.add(f);
        }
        return out;
    }

    public void markBaseline(Long runId) {
        StoredRun r = repo.findRun(runId).orElseThrow(() -> new BizException(404, "评测运行不存在"));
        if (!"DONE".equals(r.status())) throw new BizException("只能把已完成的运行设为基线");
        repo.markBaseline(runId);
    }

    // ================================================================== 小工具

    private Map<String, Object> parse(String json) {
        try {
            return om.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            throw new BizException("用例 JSON 不合法：" + e.getMessage());
        }
    }

    private String write(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException("序列化失败");
        }
    }

    private static Double dbl(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
    }
}
