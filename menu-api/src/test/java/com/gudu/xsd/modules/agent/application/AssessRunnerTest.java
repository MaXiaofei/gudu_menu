package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.application.assess.AssessDataset;
import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.agent.application.assess.AssessRunner;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 评测运行器：样例数据集在 mock（规则意图 + 模拟网关）下全部通过，报告结构完整。 */
class AssessRunnerTest {

    @Test
    @SuppressWarnings("unchecked")
    void 样例用例_mock模式全部通过_报告含指标门禁与硬门禁明细() {
        AssessDataset ds = AssessDataset.load(AgentTestKit.OM, AgentTestKit.SAMPLE);
        AssessRunner runner = new AssessRunner(new AgentOrchestrator(new ToolRegistry(),
                new com.gudu.xsd.modules.ai.AiInputGuard(), AgentTestKit.OM), new ToolRegistry(), AgentTestKit.OM,
                AssessGateways.simulated(), null);
        Map<String, Object> rep = runner.run(ds.cases(), ds.fixtures(),
                new AssessRunner.Options(2, null, ds.version(), Map.of("write_menu.today.001", false), Map.of(), "test"),
                null);
        List<Map<String, Object>> cases = (List<Map<String, Object>>) rep.get("cases");
        assertThat(cases).hasSize(7);
        for (Map<String, Object> c : cases) {
            List<Map<String, Object>> runs = (List<Map<String, Object>>) c.get("runs");
            assertThat(c.get("allPass")).as(c.get("caseId") + " → " + runs.get(0).get("failReasons")).isEqualTo(true);
            assertThat(c.get("consistent")).isEqualTo(true);
        }
        assertThat(rep.get("passed")).isEqualTo(true);
        assertThat(rep.get("provider")).isEqualTo("mock");
        assertThat((List<?>) rep.get("hardGateFailures")).hasSize(2);
        Map<String, Object> baseline = (Map<String, Object>) rep.get("baseline");
        assertThat((List<Object>) baseline.get("newFixes")).containsExactly("write_menu.today.001");
        Map<String, Object> metrics = (Map<String, Object>) rep.get("metrics");
        assertThat(metrics.get("cases")).as("指标只按 test 计").isEqualTo(4);
        assertThat(metrics.get("confirmCompliance")).isEqualTo(1.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 期望不符时给出失败原因() {
        AssessDataset ds = AssessDataset.load(AgentTestKit.OM, AgentTestKit.SAMPLE);
        Map<String, Object> broken = new java.util.LinkedHashMap<>(ds.cases().get(1));
        broken.put("id", "write_menu.broken.001");
        broken.put("expect", Map.of("tools", Map.of("mode", "subsequence",
                        "calls", List.of(Map.of("name", "add_to_menu", "args", Map.of("menu", Map.of("eq", "周六 · 爸妈来吃饭"))))),
                "actions", Map.of("maxPending", 0)));
        AssessRunner runner = new AssessRunner(new AgentOrchestrator(new ToolRegistry(),
                new com.gudu.xsd.modules.ai.AiInputGuard(), AgentTestKit.OM), new ToolRegistry(), AgentTestKit.OM,
                AssessGateways.simulated(), null);
        Map<String, Object> rep = runner.run(List.of(broken), ds.fixtures(),
                new AssessRunner.Options(1, null, "x", null, null, null), null);
        Map<String, Object> c = ((List<Map<String, Object>>) rep.get("cases")).get(0);
        Map<String, Object> run = ((List<Map<String, Object>>) c.get("runs")).get(0);
        assertThat(run.get("pass")).isEqualTo(false);
        assertThat((List<String>) run.get("failReasons")).anyMatch(r -> r.startsWith("参数"))
                .anyMatch(r -> r.contains("待确认卡片过多"));
    }
}
