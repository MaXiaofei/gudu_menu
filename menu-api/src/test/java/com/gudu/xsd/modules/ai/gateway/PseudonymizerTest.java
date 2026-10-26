package com.gudu.xsd.modules.ai.gateway;

import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.TokenUsage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolCall;
import com.gudu.xsd.modules.ai.gateway.domain.service.Pseudonymizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 成员称呼假名化 / 账号标识脱敏 / 还原（含跨 chunk 流式还原）。 */
class PseudonymizerTest {

    static final List<String> FAMILY = List.of("小王", "林林", "妈妈", "朵朵");
    final Pseudonymizer pz = Pseudonymizer.of(FAMILY);

    @Test
    void 按成员顺序稳定分配代号() {
        assertThat(pz.aliases()).containsExactly(
                java.util.Map.entry("小王", "成员A"), java.util.Map.entry("林林", "成员B"),
                java.util.Map.entry("妈妈", "成员C"), java.util.Map.entry("朵朵", "成员D"));
        assertThat(Pseudonymizer.of(FAMILY).aliases()).isEqualTo(pz.aliases());
    }

    @Test
    void 出站替换称呼_入站还原() {
        String masked = pz.mask("妈妈要少盐，朵朵吃辅食，林林和小王随意");
        assertThat(masked).isEqualTo("成员C要少盐，成员D吃辅食，成员B和成员A随意");
        assertThat(pz.restore(masked)).isEqualTo("妈妈要少盐，朵朵吃辅食，林林和小王随意");
    }

    @Test
    void 一次扫描_长名优先_不重复替换() {
        Pseudonymizer p = Pseudonymizer.of(List.of("王", "王小明", "A"));
        // 「王小明」整体优先；单字称呼「A」不会把已经替换出的「成员A」再替换一遍
        assertThat(p.mask("王小明和王")).isEqualTo("成员B和成员A");
        assertThat(p.restore(p.mask("王小明和王"))).isEqualTo("王小明和王");
        assertThat(p.mask("A")).isEqualTo("成员C");
    }

    @Test
    void 受保护的人群标签原样保留_即使成员叫宝宝() {
        Pseudonymizer p = Pseudonymizer.of(List.of("宝宝", "爸爸"));
        assertThat(p.mask("宝宝：宝宝辅食，偏好宝宝可吃；给宝宝做")).isEqualTo("成员A：宝宝辅食，偏好宝宝可吃；给成员A做");
    }

    @Test
    void 手机号_openid_wx占位一律隐藏且不还原() {
        String text = "我的手机号13812345678，账号 wx_oAbC1234，openid oXyZ_abcdefghijklmnopqrstu12，订单号 213812345678";
        String masked = pz.mask(text);
        assertThat(masked).doesNotContain("13812345678 ", "wx_", "oXyZ_abcdefghijklmnopqrstu12")
                .contains(Pseudonymizer.PHONE_PLACEHOLDER, Pseudonymizer.ID_PLACEHOLDER)
                .contains("213812345678");
        assertThat(masked).doesNotContain("号13812345678");
        assertThat(Pseudonymizer.NONE.mask("13900001111")).isEqualTo(Pseudonymizer.PHONE_PLACEHOLDER);
    }

    @Test
    void 请求出站覆盖内容_思考_工具参数_工具结果() {
        ChatRequest req = new ChatRequest(Capability.TOOLS, "agent", List.of(
                ChatMessage.system("【成员】小王,林林,妈妈,朵朵"),
                ChatMessage.user("给朵朵做点吃的"),
                ChatMessage.assistantToolCalls(null, List.of(new ToolCall("c1", "search_recipes",
                        "{\"filters\":{\"forMembers\":[\"朵朵\"]}}")), "朵朵是宝宝"),
                ChatMessage.tool("c1", "{\"members\":[{\"name\":\"妈妈\",\"tags\":[\"高血压\"]}]}")),
                List.of(), false, null, null, null, false);
        ChatRequest out = pz.mask(req);
        String all = out.messages().toString();
        FAMILY.forEach(n -> assertThat(all).doesNotContain(n));
        assertThat(out.messages().get(2).toolCalls().get(0).argumentsJson()).contains("成员D");
        assertThat(out.messages().get(3).content()).contains("成员C").contains("高血压");
    }

    @Test
    void 结果入站还原回复_思考_工具参数_且JSON转义安全() {
        Pseudonymizer p = Pseudonymizer.of(List.of("大\"宝\"", "妈妈"));
        ModelReply reply = new ModelReply("成员B今晚少盐", "成员A是宝宝",
                List.of(new ToolCall("c1", "mark_cooked", "{\"eaters\":[\"成员A\",\"成员B\"]}")), TokenUsage.ZERO);
        ChatResult r = p.restore(new ChatResult(reply, "p", "m", false, 1, BigDecimal.ZERO, List.of()));
        assertThat(r.content()).isEqualTo("妈妈今晚少盐");
        assertThat(r.reply().reasoningContent()).isEqualTo("大\"宝\"是宝宝");
        assertThat(r.reply().toolCalls().get(0).argumentsJson()).isEqualTo("{\"eaters\":[\"大\\\"宝\\\"\",\"妈妈\"]}");
        // 出站：JSON 里转义形态的称呼同样被替换
        assertThat(p.mask("{\"name\":\"大\\\"宝\\\"\"}")).isEqualTo("{\"name\":\"成员A\"}");
    }

    @Test
    void 流式还原_代号被拆在两个chunk里() {
        Pseudonymizer.StreamRestorer s = pz.streamRestorer();
        StringBuilder out = new StringBuilder();
        for (String chunk : List.of("今晚给成", "员D做蒸蛋，成员", "C的少", "盐版；家庭成员都能吃", "成")) {
            String emitted = s.push(chunk);
            assertThat(emitted).doesNotContain("成员D", "成员C");
            out.append(emitted);
        }
        out.append(s.flush());
        assertThat(out.toString()).isEqualTo("今晚给朵朵做蒸蛋，妈妈的少盐版；家庭成员都能吃成");
    }

    @Test
    void 流式还原_逐字推送也能拼回() {
        Pseudonymizer.StreamRestorer s = pz.streamRestorer();
        String src = "成员A和成员B，成员";
        StringBuilder out = new StringBuilder();
        for (char c : src.toCharArray()) out.append(s.push(String.valueOf(c)));
        out.append(s.flush());
        assertThat(out.toString()).isEqualTo("小王和林林，成员");
    }

    @Test
    void 超过26位成员_代号互不为前缀() {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) many.add("家人" + i);
        Pseudonymizer p = Pseudonymizer.of(many);
        assertThat(p.aliasOfName("家人25")).isEqualTo("成员Z");
        assertThat(p.aliasOfName("家人26")).isEqualTo("成员27");
        Pseudonymizer.StreamRestorer s = p.streamRestorer();
        String out = s.push("成员2") + s.push("7好") + s.flush();
        assertThat(out).isEqualTo("家人26好");
    }

    @Test
    void 无成员时只做标识脱敏_文本原样() {
        Pseudonymizer p = Pseudonymizer.of(List.of());
        assertThat(p.mask("妈妈要少盐")).isEqualTo("妈妈要少盐");
        assertThat(p.restore("成员A")).isEqualTo("成员A");
        assertThat(p.streamRestorer().push("成员")).isEqualTo("成员");
    }
}
