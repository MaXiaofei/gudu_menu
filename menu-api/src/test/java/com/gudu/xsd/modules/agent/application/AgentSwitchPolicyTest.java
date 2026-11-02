package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 紧急关停判定：咕嘟助手关 → 全拒（5032）；问咕嘟关 → 只拒 cook_mode（5033）；开着放行。 */
class AgentSwitchPolicyTest {

    private static final Platform MP = Platform.MP;

    /** 小程序端按给定状态，App 端永远全开。 */
    private AgentSwitchPolicy policy(boolean chat, boolean cookAsk) {
        FeatureSwitchAppService s = mock(FeatureSwitchAppService.class);
        when(s.isEnabled(FeatureKey.AGENT_CHAT, Platform.MP)).thenReturn(chat);
        when(s.isEnabled(FeatureKey.AGENT_COOK_ASK, Platform.MP)).thenReturn(cookAsk);
        when(s.isEnabled(FeatureKey.AGENT_CHAT, Platform.APP)).thenReturn(true);
        when(s.isEnabled(FeatureKey.AGENT_COOK_ASK, Platform.APP)).thenReturn(true);
        return new AgentSwitchPolicy(s);
    }

    @Test
    void 全开放行() {
        var p = policy(true, true);
        assertThat(p.checkSend(Map.of("page", "cook_mode"), MP)).isEmpty();
        assertThat(p.checkSend(null, MP)).isEmpty();
    }

    @Test
    void 咕嘟助手关闭_拦所有发消息_包括跟做模式_不看自报的page() {
        var p = policy(false, true);
        for (Map<String, Object> ctx : java.util.Arrays.asList(Map.<String, Object>of("page", "agent"),
                Map.<String, Object>of("page", "cook_mode"), Map.<String, Object>of("page", "随便填"), Map.<String, Object>of(), null)) {
            var b = p.checkSend(ctx, MP).orElseThrow();
            assertThat(b.code()).isEqualTo(5032);
            assertThat(b.message()).isEqualTo("咕嘟助手暂停服务");
        }
    }

    @Test
    void 问咕嘟关闭_只拦跟做模式_普通对话不拦() {
        var p = policy(true, false);
        var b = p.checkSend(Map.of("page", "cook_mode"), MP).orElseThrow();
        assertThat(b.code()).isEqualTo(5033);
        assertThat(b.message()).isEqualTo("问咕嘟暂停服务");
        assertThat(p.checkSend(Map.of("page", "agent"), MP)).isEmpty();
        assertThat(p.checkSend(null, MP)).isEmpty();
    }

    @Test
    void 两个都关_跟做模式先报咕嘟助手() {
        var p = policy(false, false);
        assertThat(p.checkSend(Map.of("page", "cook_mode"), MP).orElseThrow().code()).isEqualTo(5032);
    }

    @Test
    void 小程序关闭_App照常() {
        var p = policy(false, false);
        assertThat(p.checkSend(Map.of("page", "agent"), Platform.MP)).isPresent();
        assertThat(p.checkSend(Map.of("page", "agent"), Platform.APP)).isEmpty();
        assertThat(p.checkSend(Map.of("page", "cook_mode"), Platform.APP)).isEmpty();
    }
}
