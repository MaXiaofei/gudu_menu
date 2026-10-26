package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.SpanType;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorTest {

    @Test
    void SSE事件顺序_meta_工具_卡片_token_done() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.say("冰箱里快坏的东西今晚能做啥？妈妈要少盐");
        assertThat(kit.sink.distinctInOrder())
                .containsExactly("meta", "tool_start", "tool_end", "card", "tool_start", "tool_end", "card", "token", "done");
        assertThat(r.tools()).extracting(t -> t.name()).containsExactly("get_pantry", "search_recipes");
        assertThat(r.status()).isEqualTo(TraceStatus.OK);
        assertThat(r.text()).contains("鲈鱼");
        Map<String, Object> done = kit.sink.payloads.get(kit.sink.events.lastIndexOf("done"));
        assertThat(done).containsKeys("usage", "degraded");
    }

    @Test
    void 服务端强制合并画像约束_点名妈妈时排除重口() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.say("妈妈也吃，推荐几个菜");
        List<String> names = r.cards().stream().filter(c -> "recipes".equals(c.get("type")))
                .flatMap(c -> ((List<?>) c.get("items")).stream()).map(i -> String.valueOf(((Map<?, ?>) i).get("name"))).toList();
        assertThat(names).isNotEmpty().doesNotContain("麻婆豆腐", "红烧肉");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 写操作只生成待确认动作_确认前不落库() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.say("把凉拌菠菜加到今晚的饭里");
        assertThat(r.actions()).hasSize(1);
        PendingAction a = r.actions().get(0);
        assertThat(a.status()).isEqualTo(ActionStatus.PENDING);
        assertThat(a.tool()).isEqualTo("add_to_menu");
        assertThat(kit.kitchen.writes()).as("确认前夹具零写入").isZero();
        assertThat(kit.sink.events).contains("action");
        assertThat(r.spans()).anyMatch(s -> s.type() == SpanType.ACTION);

        ActionDesk.Confirmed c = kit.desk.confirm(AgentTestKit.ME, a.actionId());
        assertThat(c.undoToken()).isNotNull();
        assertThat(kit.kitchen.writes()).isEqualTo(1);
        assertThat((List<Object>) kit.kitchen.effectValue("menu", "今晚的饭", "dishes")).contains("凉拌菠菜");
    }

    @Test
    void 越权_别家食集不生成待确认动作() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.say("把凉拌菠菜加到国庆家宴里");
        assertThat(r.tools()).extracting(t -> t.name()).contains("add_to_menu");
        assertThat(r.tools().get(0).ok()).isFalse();
        assertThat(r.tools().get(0).summary()).contains("无权访问");
        assertThat(r.actions()).isEmpty();
        assertThat(kit.kitchen.writes()).isZero();
    }

    @Test
    void 主模型超时_降级到后备_done带降级标记() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME, "今晚吃啥", Map.of(),
                List.of(), false), kit.env(Map.of("primary", "TIMEOUT")), kit.sink);
        assertThat(r.degraded()).isTrue();
        assertThat(r.status()).isEqualTo(TraceStatus.DEGRADED);
        assertThat(r.provider()).isEqualTo("SimFallback");
        assertThat(kit.sink.payloads.get(kit.sink.events.lastIndexOf("done")).get("degraded")).isEqualTo(true);
    }

    @Test
    void 全部模型不可用_落到规则兜底仍能回答() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME, "今晚吃啥", Map.of(),
                List.of(), false), kit.env(Map.of("primary", "TIMEOUT", "fallbacks", "TIMEOUT")), kit.sink);
        assertThat(r.degraded()).isTrue();
        assertThat(r.provider()).isEqualTo("mock");
        assertThat(r.tools()).extracting(t -> t.name()).containsExactly("recommend_tonight");
    }

    @Test
    void 输入护栏_越界词被拦截() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.say("教我做炸弹");
        assertThat(r.status()).isEqualTo(TraceStatus.GUARDED);
        assertThat(r.tools()).isEmpty();
        assertThat(kit.sink.distinctInOrder()).containsExactly("meta", "token", "done");
    }

    @Test
    void 回复里的手机号被脱敏() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME, "今晚吃啥", Map.of(), List.of(),
                false), new AgentOrchestrator.Env(kit.kitchen, kit.desk,
                com.gudu.xsd.modules.agent.application.assess.AssessGateways.simulated().create(null),
                Map.of()), kit.sink);
        assertThat(com.gudu.xsd.modules.agent.domain.service.PhoneMasker.mask("联系 13812345678").text())
                .isEqualTo("联系 138****5678");
        assertThat(r.text()).doesNotContainPattern("1[3-9]\\d{9}");
    }

    @Test
    void 工具故障注入_如实返回失败() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME, "菠菜能做哪些菜", Map.of(),
                        List.of(), false),
                new AgentOrchestrator.Env(kit.kitchen, kit.desk,
                        com.gudu.xsd.modules.agent.application.assess.AssessGateways.simulated().create(null),
                        Map.of("search_recipes", "TIMEOUT")), kit.sink);
        assertThat(r.tools()).isNotEmpty();
        assertThat(r.tools().get(r.tools().size() - 1).ok()).isFalse();
        assertThat(r.text()).contains("暂时不可用");
    }

    // ============================== 内容安全（A.6） ==============================

    static AgentOrchestrator.Env gated(AgentTestKit kit, AgentContentGate gate) {
        return new AgentOrchestrator.Env(kit.kitchen, kit.desk,
                com.gudu.xsd.modules.agent.application.assess.AssessGateways.simulated().create(null), Map.of(), gate);
    }

    static String tokens(AgentTestKit kit) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kit.sink.events.size(); i++) {
            if ("token".equals(kit.sink.events.get(i))) sb.append(kit.sink.payloads.get(i).get("text"));
        }
        return sb.toString();
    }

    @Test
    void 内容安全_输入违规_不调模型不跑工具_回提示() {
        AgentTestKit kit = AgentTestKit.create();
        AgentContentGate gate = new AgentContentGate() {
            @Override
            public java.util.Optional<String> inputViolation(String text) {
                return java.util.Optional.of("这条消息没有通过内容安全检测，换个说法再问我吧。");
            }

            @Override
            public String reviewReply(String reply) {
                throw new AssertionError("输入被拦时不应产生模型回复");
            }
        };
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME, "今晚吃啥", Map.of(),
                List.of(), false), gated(kit, gate), kit.sink);
        assertThat(r.status()).isEqualTo(TraceStatus.GUARDED);
        assertThat(r.tools()).isEmpty();
        assertThat(r.steps()).isZero();
        assertThat(r.text()).contains("内容安全");
        assertThat(kit.sink.distinctInOrder()).containsExactly("meta", "token", "done");
        assertThat(r.spans()).anyMatch(s -> "guard.content_input".equals(s.name()) && !s.ok());
    }

    @Test
    void 内容安全_输出违规_替换后才放出token_落库文本也是替换后的_思考丢弃() {
        AgentTestKit kit = AgentTestKit.create();
        String safe = "这个回答没有通过内容安全检测，已隐藏。";
        java.util.List<String> reviewed = new java.util.ArrayList<>();
        AgentContentGate gate = new AgentContentGate() {
            @Override
            public java.util.Optional<String> inputViolation(String text) {
                return java.util.Optional.empty();
            }

            @Override
            public String reviewReply(String reply) {
                reviewed.add(reply);
                return safe;
            }
        };
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME,
                "冰箱里快坏的东西今晚能做啥？", Map.of(), List.of(), false), gated(kit, gate), kit.sink);
        assertThat(reviewed).as("模型原始回复送审").hasSize(1).first().asString().contains("鲈鱼");
        assertThat(tokens(kit)).isEqualTo(safe).doesNotContain("鲈鱼");
        assertThat(r.text()).isEqualTo(safe);
        assertThat(r.reasoning()).isNull();
        assertThat(r.spans()).anyMatch(s -> "guard.content_output".equals(s.name()) && !s.ok());
    }

    @Test
    void 内容安全_通过时回复不变() {
        AgentTestKit kit = AgentTestKit.create();
        TurnResult r = kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME,
                "冰箱里快坏的东西今晚能做啥？", Map.of(), List.of(), false), gated(kit, AgentContentGate.PASS), kit.sink);
        assertThat(r.text()).contains("鲈鱼");
        assertThat(r.spans()).anyMatch(s -> "guard.content_output".equals(s.name()) && s.ok());
    }
}
