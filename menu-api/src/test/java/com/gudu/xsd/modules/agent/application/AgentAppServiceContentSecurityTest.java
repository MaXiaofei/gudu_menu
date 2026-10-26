package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.application.dto.AgentViews.SendCommand;
import com.gudu.xsd.modules.agent.application.kitchen.LiveKitchenFactory;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.domain.model.AgentMessage;
import com.gudu.xsd.modules.agent.domain.model.AgentSession;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.SpeechRecognizer;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentQuota;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.agent.domain.repository.PendingActionStore;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 内容安全接入 AgentAppService：按登录账号检测；违规输入不落原文、不进后续历史；输出复核按登录账号。 */
class AgentAppServiceContentSecurityTest {

    static final long MEMBER = 5L;   // 当前就餐成员（可能是家庭成员档案）
    static final long ACCOUNT = 1L;  // 登录账号（有 openid）

    AgentMessageRepository messages;
    AgentOrchestrator orchestrator;
    ContentSecurityAppService contentSecurity;
    AgentAppService svc;

    @BeforeEach
    void setUp() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        when(sessions.findById("ses_1")).thenReturn(Optional.of(
                AgentSession.start("ses_1", MEMBER, "E2E会话", LocalDateTime.of(2026, 10, 26, 10, 0))));
        messages = mock(AgentMessageRepository.class);
        orchestrator = mock(AgentOrchestrator.class);
        when(orchestrator.run(any(), any(), any())).thenThrow(new IllegalStateException("只验证进入编排器前的处理"));
        LiveKitchenFactory kitchens = mock(LiveKitchenFactory.class);
        when(kitchens.forMember(any())).thenReturn(mock(Kitchen.class));
        contentSecurity = mock(ContentSecurityAppService.class);
        svc = new AgentAppService(sessions, messages, mock(AgentTraceRepository.class), mock(PendingActionStore.class),
                mock(AgentQuota.class), orchestrator, new ToolRegistry(), kitchens, mock(ModelGateway.class),
                mock(SpeechRecognizer.class), Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
                50, contentSecurity);
    }

    @Test
    void 输入违规_用户消息落占位文本_编排器拿到占位与违规结论() {
        when(contentSecurity.agentInputViolation(ACCOUNT, "违规原话")).thenReturn(Optional.of("换个说法"));
        svc.send(MEMBER, ACCOUNT, "ses_1", new SendCommand("违规原话", null, null, null), AgentEventSink.NOOP);

        ArgumentCaptor<AgentMessage> saved = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().text()).isEqualTo(AgentAppService.BLOCKED_INPUT_PLACEHOLDER);
        ArgumentCaptor<TurnRequest> req = ArgumentCaptor.forClass(TurnRequest.class);
        ArgumentCaptor<AgentOrchestrator.Env> env = ArgumentCaptor.forClass(AgentOrchestrator.Env.class);
        verify(orchestrator).run(req.capture(), env.capture(), any());
        assertThat(req.getValue().text()).isEqualTo(AgentAppService.BLOCKED_INPUT_PLACEHOLDER);
        assertThat(env.getValue().contentGate().inputViolation(req.getValue().text())).contains("换个说法");
    }

    @Test
    void 输入通过_原文落库_输出复核走登录账号() {
        when(contentSecurity.agentInputViolation(ACCOUNT, "今晚吃啥")).thenReturn(Optional.empty());
        when(contentSecurity.reviewAgentReply(ACCOUNT, "回复")).thenReturn("安全回复");
        svc.send(MEMBER, ACCOUNT, "ses_1", new SendCommand("今晚吃啥", null, null, null), AgentEventSink.NOOP);

        ArgumentCaptor<AgentMessage> saved = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().text()).isEqualTo("今晚吃啥");
        ArgumentCaptor<AgentOrchestrator.Env> env = ArgumentCaptor.forClass(AgentOrchestrator.Env.class);
        verify(orchestrator).run(any(), env.capture(), any());
        assertThat(env.getValue().contentGate().inputViolation("今晚吃啥")).isEmpty();
        assertThat(env.getValue().contentGate().reviewReply("回复")).isEqualTo("安全回复");
        verify(contentSecurity).reviewAgentReply(ACCOUNT, "回复");
        verify(contentSecurity, org.mockito.Mockito.never()).agentInputViolation(org.mockito.ArgumentMatchers.eq(MEMBER), anyString());
    }
}
