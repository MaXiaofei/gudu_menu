package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.application.dto.AgentViews.FullTrace;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.MessageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.PageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ToolView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.TraceDetail;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.TraceRow;
import com.gudu.xsd.modules.agent.domain.model.AgentMessage;
import com.gudu.xsd.modules.agent.domain.model.AgentTrace;
import com.gudu.xsd.modules.agent.domain.model.SpanType;
import com.gudu.xsd.modules.agent.domain.model.TraceSpan;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 后台咕嘟调用链：列表 / 详情 / 对话默认只给脱敏摘要；全文走 full()。 */
class AgentTracePrivacyTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0);
    static final String LONG = "我是妈咪阿美，手机13812345678，openid 是 oAbCdEfGhIjKlMnOpQrStUvWxYz1，"
            + "帮我安排这周的晚餐，要清淡一点，孩子不吃辣，也别放香菜，最好二十分钟能做完，谢谢你啦";

    final AgentTraceRepository traces = mock(AgentTraceRepository.class);
    final AgentMessageRepository messages = mock(AgentMessageRepository.class);
    final AgentAppService agent = mock(AgentAppService.class);
    final MemberService members = mock(MemberService.class);
    final AgentTraceAppService svc = new AgentTraceAppService(traces, messages, agent, members,
            Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

    AgentTrace trace() {
        return new AgentTrace("t1", "s1", "m1", 7L, LONG, TraceStatus.OK, "deepseek", "flash", "v1", false, 900, 300L,
                10, 20, BigDecimal.ONE, 1, null, NOW);
    }

    void givenOwner() {
        Member m = new Member();
        m.setId(7L);
        m.setName("阿美");
        when(members.listByIds(any())).thenReturn(List.of(m));
    }

    @Test
    void 列表_脱敏截断_不显示昵称头像() {
        givenOwner();
        when(traces.page(any(), any(), any(), any(), anyInt())).thenReturn(List.of(trace()));

        PageView<TraceRow> page = svc.page(null, 10, null, null);

        TraceRow row = page.items().get(0);
        assertThat(row.firstText()).doesNotContain("13812345678").doesNotContain("阿美").doesNotContain("oAbCdEfGhIjKlMnOpQrStUvWxYz1");
        assertThat(row.firstText()).contains("成员A").hasSizeLessThanOrEqualTo(AgentTraceAppService.BRIEF_FIRST_TEXT + 1);
        assertThat(row.user().nickname()).isEqualTo("用户7");
        assertThat(row.user().avatarUrl()).isNull();
    }

    @Test
    void 详情_片段输入输出脱敏截断_头部不含昵称() {
        givenOwner();
        when(traces.find("t1")).thenReturn(Optional.of(trace()));
        when(traces.spans("t1")).thenReturn(List.of(new TraceSpan("a", null, SpanType.LLM, "llm.plan", 0, 10, 5,
                BigDecimal.ZERO, true, Map.of("text", LONG, "n", 3), Map.of("list", List.of("13812345678")))));

        TraceDetail d = svc.detail("t1");

        assertThat(d.trace().user().nickname()).isEqualTo("用户7");
        assertThat(d.trace().firstText()).doesNotContain("13812345678");
        String in = (String) d.spans().get(0).input().get("text");
        assertThat(in).doesNotContain("13812345678").doesNotContain("阿美")
                .hasSizeLessThanOrEqualTo(AgentTraceAppService.BRIEF_TEXT + 1);
        assertThat(d.spans().get(0).input().get("n")).isEqualTo(3);
        assertThat(d.spans().get(0).output().get("list").toString()).doesNotContain("13812345678");
    }

    @Test
    void 对话_默认摘要_截断且不含工具卡片动作() {
        givenOwner();
        when(traces.find("t1")).thenReturn(Optional.of(trace()));
        AgentMessage raw = mock(AgentMessage.class);
        when(messages.all("s1")).thenReturn(List.of(raw));
        when(agent.view(raw)).thenReturn(new MessageView("m1", "user", LONG,
                List.of(new ToolView("get_pantry", "查余量", "有 3 样", 5, true)), List.of(Map.of("k", "v")), List.of(),
                "t1", null, NOW));

        List<MessageView> list = svc.conversation("t1");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).text()).doesNotContain("13812345678").hasSizeLessThanOrEqualTo(AgentTraceAppService.BRIEF_TEXT + 1);
        assertThat(list.get(0).tools()).isEmpty();
        assertThat(list.get(0).cards()).isEmpty();
    }

    @Test
    void 全文_原样返回并带所属账号() {
        givenOwner();
        when(traces.find("t1")).thenReturn(Optional.of(trace()));
        when(traces.spans("t1")).thenReturn(List.of());
        AgentMessage raw = mock(AgentMessage.class);
        when(messages.all("s1")).thenReturn(List.of(raw));
        when(agent.view(raw)).thenReturn(new MessageView("m1", "user", LONG, List.of(), List.of(), List.of(), "t1",
                null, NOW));

        FullTrace f = svc.full("t1");

        assertThat(f.accountId()).isEqualTo(7L);
        assertThat(f.detail().trace().firstText()).isEqualTo(LONG);
        assertThat(f.detail().trace().user().nickname()).isEqualTo("阿美");
        assertThat(f.messages().get(0).text()).isEqualTo(LONG);
    }
}
