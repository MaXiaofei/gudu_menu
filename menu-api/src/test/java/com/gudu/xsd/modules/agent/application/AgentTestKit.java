package com.gudu.xsd.modules.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.agent.application.assess.AssessDataset;
import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.agent.application.assess.FixtureKitchen;
import com.gudu.xsd.modules.agent.application.assess.InMemoryActionStore;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.ai.AiInputGuard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 测试夹具：样例数据集（src/test/resources/agent-assess-sample）+ 模拟网关 + 内存动作存储。 */
public final class AgentTestKit {

    public static final ObjectMapper OM = new ObjectMapper().findAndRegisterModules();
    public static final String SAMPLE = "agent-assess-sample";
    public static final long ME = 2001L;

    public final FixtureKitchen kitchen;
    public final InMemoryActionStore store = new InMemoryActionStore();
    public final ActionDesk desk;
    public final AgentOrchestrator orchestrator = new AgentOrchestrator(new ToolRegistry(), new AiInputGuard(), OM);
    public final Recorder sink = new Recorder();

    private AgentTestKit(Map<String, Object> patch) {
        this.kitchen = new FixtureKitchen(fixture(), OM);
        kitchen.applyPatch(patch);
        this.desk = new ActionDesk(store, kitchen);
    }

    public static AgentTestKit create() {
        return new AgentTestKit(null);
    }

    public static AgentTestKit withPatch(Map<String, Object> patch) {
        return new AgentTestKit(patch);
    }

    public static Map<String, Object> fixture() {
        return AssessDataset.load(OM, SAMPLE).fixtures().get("demo-family");
    }

    public AgentOrchestrator.Env env(Map<String, Object> gatewayPatch) {
        return new AgentOrchestrator.Env(kitchen, desk, AssessGateways.simulated().create(gatewayPatch), Map.of());
    }

    public TurnResult say(String text) {
        return orchestrator.run(new TurnRequest("s1", "m1", "t1", ME, text, Map.of(), List.of(), false), env(null), sink);
    }

    /** 记录 SSE 事件。 */
    public static final class Recorder implements AgentEventSink {
        public final List<String> events = new ArrayList<>();
        public final List<Map<String, Object>> payloads = new ArrayList<>();

        @Override
        public void send(String event, Map<String, Object> data) {
            events.add(event);
            payloads.add(new LinkedHashMap<>(data));
        }

        public List<String> distinctInOrder() {
            List<String> out = new ArrayList<>();
            for (String e : events) if (out.isEmpty() || !out.get(out.size() - 1).equals(e)) out.add(e);
            return out;
        }
    }
}
