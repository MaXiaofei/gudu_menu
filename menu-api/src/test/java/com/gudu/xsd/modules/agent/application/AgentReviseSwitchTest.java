package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.agent.application.kitchen.LiveKitchenFactory;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.SpeechRecognizer;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentQuota;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.agent.domain.repository.PendingActionStore;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 修改待确认卡（revise）会重跑原工具，必须带请求端判一周餐程开关：
 * 小程序端 mp 开、App 关时，小程序用户修改 plan_week 卡片不能被误判「暂停服务」；mp 关时才被拒。
 */
class AgentReviseSwitchTest {

    static final long MEMBER = 5L;
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 19, 0);

    LiveKitchenFactory kitchens;
    Kitchen mpKitchen;
    Kitchen appKitchen;
    PendingActionStore store;
    AgentAppService svc;

    @BeforeEach
    void setUp() {
        kitchens = mock(LiveKitchenFactory.class);
        mpKitchen = mock(Kitchen.class);
        appKitchen = mock(Kitchen.class);
        for (Kitchen k : List.of(mpKitchen, appKitchen)) {
            when(k.now()).thenReturn(NOW);
            when(k.draftWeekPlan(any(), any(), any())).thenReturn(Optional.empty());
            when(k.members()).thenReturn(List.of());
            when(k.searchDishes(any())).thenReturn(List.of());
        }
        when(mpKitchen.mealPlanEnabled()).thenReturn(true);   // 小程序开
        when(appKitchen.mealPlanEnabled()).thenReturn(false); // App 默认关
        when(kitchens.forMember(MEMBER, Platform.MP)).thenReturn(mpKitchen);
        when(kitchens.forMember(MEMBER, Platform.APP)).thenReturn(appKitchen);
        store = mock(PendingActionStore.class);
        PendingAction card = PendingAction.draft("act_1", "ses_1", "msg_1", MEMBER, "plan_week", "排一周",
                Map.of("_raw", Map.of()), Map.of(), NOW);
        when(store.find("act_1")).thenReturn(Optional.of(card));
        svc = new AgentAppService(mock(AgentSessionRepository.class), mock(AgentMessageRepository.class),
                mock(AgentTraceRepository.class), store, mock(AgentQuota.class), mock(AgentOrchestrator.class),
                new ToolRegistry(), kitchens, mock(ModelGateway.class), mock(SpeechRecognizer.class),
                Clock.fixed(Instant.parse("2026-10-26T11:00:00Z"), ZoneId.of("Asia/Shanghai")), 50,
                mock(ContentSecurityAppService.class),
                mock(com.gudu.xsd.modules.preference.application.UserPreferenceAppService.class),
                mock(com.gudu.xsd.modules.recommend.application.HomeRecommendAppService.class));
    }

    @Test
    void 小程序端mp开_App关_修改plan_week卡片不被误判暂停() {
        // plan_week 是读工具、没有待确认草稿，所以重跑后抛的是「没有符合约束的菜」之类的业务失败，而不是暂停提示
        assertThatThrownBy(() -> svc.revise(MEMBER, "act_1", Map.of(), Platform.MP))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("暂停服务"));
        verify(kitchens).forMember(MEMBER, Platform.MP);
        verify(kitchens, never()).forMember(MEMBER, Platform.APP);
        verify(mpKitchen).mealPlanEnabled();
    }

    @Test
    void mp关停_修改被拒_提示暂停() {
        when(mpKitchen.mealPlanEnabled()).thenReturn(false);
        assertThatThrownBy(() -> svc.revise(MEMBER, "act_1", Map.of(), Platform.MP))
                .isInstanceOf(BizException.class).hasMessageContaining("一周餐程暂停服务");
    }

    @Test
    void App端关停_修改被拒() {
        assertThatThrownBy(() -> svc.revise(MEMBER, "act_1", Map.of(), Platform.APP))
                .isInstanceOf(BizException.class).hasMessageContaining("一周餐程暂停服务");
        verify(kitchens).forMember(MEMBER, Platform.APP);
    }
}
