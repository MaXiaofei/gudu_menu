package com.gudu.xsd.modules.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gudu.xsd.modules.agent.application.AgentOrchestrator;
import com.gudu.xsd.modules.agent.application.assess.AssessDataset;
import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.agent.application.assess.AssessRunner;
import com.gudu.xsd.modules.agent.application.assess.LlmJudge;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.ai.AiInputGuard;
import com.gudu.xsd.modules.ai.gateway.infrastructure.OpenAiCompatibleTransport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 咕嘟评测命令行入口（默认不随 mvn test 跑）：
 * <pre>
 * ./mvnw test -Dgroups=eval -Dtest=AgentAssessCliTest \
 *     -Deval.split=test -Deval.provider=mock|gateway [-Deval.runs=3] [-Deval.dataset=agent-eval]
 *     [-Deval.only=write_menu.,safety_] [-Deval.baseline=target/agent-eval-baseline.json] [-Deval.enforce=true]
 * </pre>
 * mock：规则意图 + 模拟网关，无外部调用，不判 rubric；gateway：内置默认路由（DeepSeek → GLM → 规则）+ 环境变量 key，
 * 带 LLM 裁判。报告写到 {@code target/agent-eval-report.json}；{@code eval.enforce=true} 时门禁不过则失败（CI 用）。
 */
@Tag("eval")
class AgentAssessCliTest {

    @Test
    @SuppressWarnings("unchecked")
    void run() throws Exception {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
        String split = blankToNull(System.getProperty("eval.split", "test"));
        if ("all".equalsIgnoreCase(split)) split = null;
        String provider = System.getProperty("eval.provider", "mock");
        int runs = Integer.parseInt(System.getProperty("eval.runs", "3"));
        String datasetRoot = System.getProperty("eval.dataset", AssessDataset.ROOT);
        AssessDataset ds = AssessDataset.load(om, datasetRoot);
        assertThat(ds.cases()).as("数据集为空：" + datasetRoot).isNotEmpty();
        List<Map<String, Object>> cases = ds.cases();
        String only = blankToNull(System.getProperty("eval.only"));
        if (only != null) {
            List<String> prefixes = List.of(only.split(","));
            cases = cases.stream().filter(c -> prefixes.stream().anyMatch(p -> String.valueOf(c.get("id")).startsWith(p.trim())))
                    .toList();
        }

        AssessGateways.Factory gateways;
        LlmJudge judge = null;
        if ("gateway".equalsIgnoreCase(provider)) {
            OpenAiCompatibleTransport transport = new OpenAiCompatibleTransport(om);
            gateways = AssessGateways.defaults(transport, System::getenv);
            judge = new LlmJudge(gateways.create(null), om);
        } else {
            gateways = AssessGateways.simulated();
        }

        Map<String, Boolean> baseCases = new LinkedHashMap<>();
        Map<String, Double> baseCats = new LinkedHashMap<>();
        String baselinePath = System.getProperty("eval.baseline");
        if (baselinePath != null && new File(baselinePath).isFile()) {
            Map<String, Object> base = om.readValue(new File(baselinePath), Map.class);
            for (Map<String, Object> c : (List<Map<String, Object>>) base.getOrDefault("cases", List.of())) {
                baseCases.put(String.valueOf(c.get("caseId")), Boolean.TRUE.equals(c.get("allPass")));
            }
            for (Map<String, Object> c : (List<Map<String, Object>>) base.getOrDefault("byCategory", List.of())) {
                baseCats.put(String.valueOf(c.get("category")), ((Number) c.get("score")).doubleValue());
            }
        }

        AssessRunner runner = new AssessRunner(new AgentOrchestrator(new ToolRegistry(), new AiInputGuard(), om),
                new ToolRegistry(), om, gateways, judge);
        Map<String, Object> report = runner.run(cases, ds.fixtures(),
                new AssessRunner.Options(runs, split, ds.version(), baseCases, baseCats, baselinePath),
                line -> System.out.println("[assess] " + line));
        File out = new File("target/agent-eval-report.json");
        out.getParentFile().mkdirs();
        om.writeValue(out, report);
        System.out.println("[assess] 报告：" + out.getAbsolutePath());
        System.out.println("[assess] 指标：" + report.get("metrics"));
        System.out.println("[assess] 门禁：" + (Boolean.TRUE.equals(report.get("passed")) ? "通过" : "未通过"));

        assertThat(out).exists();
        if (Boolean.parseBoolean(System.getProperty("eval.enforce", "false"))) {
            assertThat(report.get("passed")).as("门禁未通过，见 " + out).isEqualTo(true);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
