package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 隐私政策第三节：咕嘟助手发给模型的消息（系统提示 / 历史 / 用户输入 / 工具结果 / 工具调用参数）不含真实成员称呼、
 * 手机号、openid、wx_ 占位手机号；模型输出与工具参数还原为真实称呼后再执行与下发。
 */
class AgentPrivacyTest {

    /** 夹具 demo-family 的成员称呼（含当前用户自己的昵称「小王」）。 */
    static final List<String> REAL_NAMES = List.of("小王", "林林", "妈妈", "朵朵");
    static final List<String> IDENTIFIERS = List.of("13812345678", "wx_oAbc1234", "oXyZ_abcdefghijklmnopqrstu12");

    final AgentTestKit kit = AgentTestKit.create();
    final List<ChatRequest> sent = new ArrayList<>();

    TurnResult say(String text, List<ChatMessage> history) {
        return kit.orchestrator.run(new TurnRequest("s1", "m1", "t1", AgentTestKit.ME, text, Map.of(), history, false),
                new AgentOrchestrator.Env(kit.kitchen, kit.desk, AssessGateways.simulated(sent::add).create(null), Map.of()),
                kit.sink);
    }

    static String dump(ChatRequest r) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : r.messages()) {
            sb.append(m.content()).append('\n').append(m.reasoningContent()).append('\n');
            m.toolCalls().forEach(c -> sb.append(c.argumentsJson()).append('\n'));
        }
        return sb.toString();
    }

    void assertNothingPersonalSent() {
        assertThat(sent).as("至少调用了一次模型").isNotEmpty();
        for (ChatRequest r : sent) {
            String all = dump(r);
            for (String n : REAL_NAMES) assertThat(all).as("发给模型的消息不应含称呼「%s」", n).doesNotContain(n);
            for (String id : IDENTIFIERS) assertThat(all).doesNotContain(id);
            assertThat(all).doesNotContainPattern("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
        }
    }

    @Test
    void 系统提示_历史_用户输入_工具结果都已假名化() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("上次给朵朵做了蒸蛋，我账号 wx_oAbc1234，openid oXyZ_abcdefghijklmnopqrstu12"),
                ChatMessage.assistant("好的，林林和小王也爱吃"));
        TurnResult r = say("冰箱里快坏的东西今晚能做啥？妈妈要少盐，有事打我 13812345678", history);

        assertNothingPersonalSent();
        String system = sent.get(0).messages().get(0).content();
        assertThat(system).contains("【成员】成员A,成员B,成员C,成员D").contains("成员C：高血压");
        assertThat(sent.get(sent.size() - 1).messages()).anyMatch(m -> "tool".equals(m.role()));
        // 模型（规则模拟）用代号点名「成员C」→ 工具实际收到的是真实称呼
        assertThat(r.toolArgs().toString()).doesNotContain("成员C");
        assertThat(r.toolArgs().toString()).contains("forMembers=[妈妈]");
        assertThat(r.text()).doesNotContain("成员A", "成员B", "成员C", "成员D");
    }

    @Test
    void 读取家庭档案的工具结果回给模型前替换称呼() {
        TurnResult r = say("家里每个人有什么忌口", List.of());
        assertNothingPersonalSent();
        boolean profileRead = r.tools().stream().anyMatch(t -> "get_family_profile".equals(t.name()));
        if (profileRead) {
            String toolMsgs = sent.stream().flatMap(q -> q.messages().stream()).filter(m -> "tool".equals(m.role()))
                    .map(ChatMessage::content).reduce("", String::concat);
            assertThat(toolMsgs).contains("成员C").contains("高血压");
        }
    }

    @Test
    void 工具调用参数还原为真实成员_动作不含代号() {
        TurnResult r = say("朵朵和妈妈中午吃了番茄炒蛋，记一下", List.of());
        assertNothingPersonalSent();
        String actions = r.actions().toString() + r.toolArgs();
        assertThat(r.toolArgs().toString()).as("模型用代号点名，工具收到真实称呼").contains("forMembers=[妈妈, 朵朵]");
        assertThat(actions).doesNotContain("成员A", "成员B", "成员C", "成员D");
    }

    @Test
    void SSE_token_拼起来等于还原后的回复_不出现代号() {
        TurnResult r = say("给朵朵推荐个晚饭", List.of());
        StringBuilder tokens = new StringBuilder();
        for (int i = 0; i < kit.sink.events.size(); i++) {
            if ("token".equals(kit.sink.events.get(i))) tokens.append(kit.sink.payloads.get(i).get("text"));
        }
        assertThat(tokens.toString()).isEqualTo(r.text());
        assertThat(tokens.toString()).doesNotContain("成员D");
        assertNothingPersonalSent();
    }

    @Test
    void 代号被拆在两个token里也能还原() {
        AgentTestKit.Recorder rec = new AgentTestKit.Recorder();
        RestoringSink sink = new RestoringSink(rec,
                com.gudu.xsd.modules.ai.gateway.domain.service.Pseudonymizer.of(REAL_NAMES));
        sink.send("token", Map.of("text", "给成员"));
        sink.send("token", Map.of("text", "D做蒸蛋，成"));
        sink.send("token", Map.of("text", "员C少盐"));
        sink.send("card", Map.of("type", "recipes"));
        sink.send("token", Map.of("text", "成"));
        sink.send("done", Map.of());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < rec.events.size(); i++) {
            if ("token".equals(rec.events.get(i))) text.append(rec.payloads.get(i).get("text"));
        }
        assertThat(text.toString()).isEqualTo("给朵朵做蒸蛋，妈妈少盐成");
        assertThat(rec.events.get(rec.events.size() - 1)).isEqualTo("done");
        assertThat(rec.events).contains("card");
    }
}
