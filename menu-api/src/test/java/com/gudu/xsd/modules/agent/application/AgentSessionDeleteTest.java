package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.agent.application.kitchen.LiveKitchenFactory;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.domain.model.AgentSession;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用户自行删除 AI 对话（隐私政策 §三.2）：只能删本人档案的会话，越权 404；消息物理删、调用链去标识。 */
class AgentSessionDeleteTest {

    static final long ME = 5L;     // 当前档案 id（不是账号 id）
    static final long OTHER = 6L;

    AgentSessionRepository sessions;
    AgentMessageRepository messages;
    AgentTraceRepository traces;
    AgentAppService svc;

    @BeforeEach
    void setUp() {
        sessions = mock(AgentSessionRepository.class);
        messages = mock(AgentMessageRepository.class);
        traces = mock(AgentTraceRepository.class);
        LocalDateTime t = LocalDateTime.of(2026, 10, 26, 10, 0);
        when(sessions.findById("ses_mine")).thenReturn(Optional.of(AgentSession.start("ses_mine", ME, "我的", t)));
        when(sessions.findById("ses_other")).thenReturn(Optional.of(AgentSession.start("ses_other", OTHER, "别人的", t)));
        when(sessions.findById("ses_none")).thenReturn(Optional.empty());
        LiveKitchenFactory kitchens = mock(LiveKitchenFactory.class);
        svc = new AgentAppService(sessions, messages, traces, mock(PendingActionStore.class), mock(AgentQuota.class),
                mock(AgentOrchestrator.class), new ToolRegistry(), kitchens, mock(ModelGateway.class),
                mock(SpeechRecognizer.class), Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
                50, mock(ContentSecurityAppService.class),
                mock(com.gudu.xsd.modules.preference.application.UserPreferenceAppService.class),
                mock(com.gudu.xsd.modules.recommend.application.HomeRecommendAppService.class));
    }

    @Test
    void 删除本人会话_消息物理删_调用链去标识_会话物理删() {
        svc.deleteSession(ME, "ses_mine");

        verify(messages).deleteBySessions(List.of("ses_mine"));
        verify(traces).anonymizeBySessions(List.of("ses_mine"));
        verify(sessions).hardDelete(List.of("ses_mine"));
    }

    @Test
    void 删除别人的会话_404且不动任何数据() {
        assertThatThrownBy(() -> svc.deleteSession(ME, "ses_other"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
        verify(messages, never()).deleteBySessions(any());
        verify(traces, never()).anonymizeBySessions(any());
        verify(sessions, never()).hardDelete(any());
    }

    @Test
    void 删除不存在的会话_404() {
        assertThatThrownBy(() -> svc.deleteSession(ME, "ses_none"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
    }

    @Test
    void 清空_只按当前档案id取会话() {
        when(sessions.idsByMembers(List.of(ME))).thenReturn(List.of("ses_a", "ses_b"));

        assertThat(svc.clearSessions(ME)).isEqualTo(2);

        verify(sessions).idsByMembers(List.of(ME));
        verify(messages).deleteBySessions(List.of("ses_a", "ses_b"));
        verify(traces).anonymizeBySessions(List.of("ses_a", "ses_b"));
        verify(sessions).hardDelete(List.of("ses_a", "ses_b"));
    }

    @Test
    void 清空_无会话时返回0不报错() {
        when(sessions.idsByMembers(List.of(ME))).thenReturn(List.of());

        assertThat(svc.clearSessions(ME)).isZero();
        verify(sessions, never()).hardDelete(any());
    }
}
