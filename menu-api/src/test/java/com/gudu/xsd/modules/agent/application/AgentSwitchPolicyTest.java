package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 紧急关停判定：cook_mode 看问咕嘟开关，其余看咕嘟助手开关；开着放行。 */
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
    void 按上下文页面选开关() {
        assertThat(AgentSwitchPolicy.keyOf(Map.of("page", "cook_mode"))).isEqualTo(FeatureKey.AGENT_COOK_ASK);
        assertThat(AgentSwitchPolicy.keyOf(Map.of("page", "agent"))).isEqualTo(FeatureKey.AGENT_CHAT);
        assertThat(AgentSwitchPolicy.keyOf(Map.of())).isEqualTo(FeatureKey.AGENT_CHAT);
        assertThat(AgentSwitchPolicy.keyOf(null)).isEqualTo(FeatureKey.AGENT_CHAT);
        Map<String, Object> nullPage = new HashMap<>();
        nullPage.put("page", null);
        assertThat(AgentSwitchPolicy.keyOf(nullPage)).isEqualTo(FeatureKey.AGENT_CHAT);
    }

    @Test
    void 全开放行() {
        var p = policy(true, true);
        assertThat(p.checkSend(Map.of("page", "cook_mode"), MP)).isEmpty();
        assertThat(p.checkSend(null, MP)).isEmpty();
    }

    @Test
    void 咕嘟助手关闭_拦普通对话_不拦跟做模式问咕嘟() {
        var p = policy(false, true);
        var b = p.checkSend(Map.of("page", "agent"), MP).orElseThrow();
        assertThat(b.code()).isEqualTo(5032);
        assertThat(b.message()).isEqualTo("咕嘟助手暂停服务");
        assertThat(p.checkSend(Map.of("page", "cook_mode"), MP)).isEmpty();
    }

    @Test
    void 问咕嘟关闭_只拦跟做模式() {
        var p = policy(true, false);
        var b = p.checkSend(Map.of("page", "cook_mode"), MP).orElseThrow();
        assertThat(b.code()).isEqualTo(5033);
        assertThat(b.message()).isEqualTo("问咕嘟暂停服务");
        assertThat(p.checkSend(Map.of("page", "agent"), MP)).isEmpty();
    }

    @Test
    void 小程序关闭_App照常() {
        var p = policy(false, false);
        assertThat(p.checkSend(Map.of("page", "agent"), Platform.MP)).isPresent();
        assertThat(p.checkSend(Map.of("page", "agent"), Platform.APP)).isEmpty();
        assertThat(p.checkSend(Map.of("page", "cook_mode"), Platform.APP)).isEmpty();
    }
}
