package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.application.dto.AgentViews.SuggestionsView;
import com.gudu.xsd.modules.agent.application.kitchen.LiveKitchenFactory;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.SpeechRecognizer;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentQuota;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.agent.domain.repository.PendingActionStore;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.preference.application.UserPreferenceAppService;
import com.gudu.xsd.modules.recommend.application.HomeRecommendAppService;
import com.gudu.xsd.modules.recommend.application.dto.DishCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A.8：关闭个性化推荐后，咕嘟首屏建议不建厨房上下文（不读成员档案 / 库存 / 做饭记录）。 */
class AgentSuggestionsPreferenceTest {

    static final long MEMBER = 5L;
    static final long ACCOUNT = 1L;

    LiveKitchenFactory kitchens;
    Kitchen kitchen;
    UserPreferenceAppService preferences;
    HomeRecommendAppService homeRecommend;
    AgentAppService svc;

    @BeforeEach
    void setUp() {
        kitchens = mock(LiveKitchenFactory.class);
        kitchen = mock(Kitchen.class);
        when(kitchens.forMember(any())).thenReturn(kitchen);
        when(kitchen.now()).thenReturn(LocalDateTime.of(2026, 10, 26, 19, 0));
        preferences = mock(UserPreferenceAppService.class);
        homeRecommend = mock(HomeRecommendAppService.class);
        svc = new AgentAppService(mock(AgentSessionRepository.class), mock(AgentMessageRepository.class),
                mock(AgentTraceRepository.class), mock(PendingActionStore.class), mock(AgentQuota.class),
                mock(AgentOrchestrator.class), new ToolRegistry(), kitchens, mock(ModelGateway.class),
                mock(SpeechRecognizer.class),
                Clock.fixed(Instant.parse("2026-10-26T11:00:00Z"), ZoneId.of("Asia/Shanghai")),
                50, mock(ContentSecurityAppService.class), preferences, homeRecommend);
    }

    @Test
    void 关闭个性化_不读个人数据_只给通用问候与时令菜() {
        when(preferences.personalizedRecommendEnabled(ACCOUNT)).thenReturn(false);
        when(homeRecommend.genericPicks(1)).thenReturn(List.of(
                new DishCard(9L, "清炒山药", "/c.jpg", 15, 1, 0, null)));

        SuggestionsView v = svc.suggestions(MEMBER, ACCOUNT);

        verifyNoInteractions(kitchens);
        verifyNoInteractions(kitchen);
        assertThat(v.greeting()).isEqualTo("晚上好");
        assertThat(v.expiring()).isEmpty();
        assertThat(v.tonight()).containsEntry("id", 9L).containsEntry("name", "清炒山药");
        assertThat(v.prompts()).isEqualTo(AgentAppService.GENERIC_PROMPTS);
    }

    @Test
    void 关闭个性化_通用推荐为空_tonight为null() {
        when(preferences.personalizedRecommendEnabled(ACCOUNT)).thenReturn(false);
        when(homeRecommend.genericPicks(anyInt())).thenReturn(List.of());
        assertThat(svc.suggestions(MEMBER, ACCOUNT).tonight()).isNull();
        verifyNoInteractions(kitchens);
    }

    @Test
    void 开启个性化_走厨房上下文_不调通用推荐() {
        when(preferences.personalizedRecommendEnabled(ACCOUNT)).thenReturn(true);
        when(kitchen.members()).thenReturn(List.of());
        when(kitchen.pantry()).thenReturn(List.of());
        when(kitchen.recommendTonight(1, Set.of())).thenReturn(List.of());

        svc.suggestions(MEMBER, ACCOUNT);

        verify(kitchens).forMember(MEMBER);
        verify(homeRecommend, never()).genericPicks(anyInt());
    }
}
