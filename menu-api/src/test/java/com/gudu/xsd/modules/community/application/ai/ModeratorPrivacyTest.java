package com.gudu.xsd.modules.community.application.ai;

import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 社区 AI 预审：帖子里的手机号 / openid 不发给模型，但保留占位符（引流广告判定仍有信号）。 */
class ModeratorPrivacyTest {

    @Test
    void 预审出站隐藏账号标识_保留占位信号() {
        List<ChatRequest> sent = new ArrayList<>();
        LlmPostModerator m = new LlmPostModerator(AssessGateways.simulated(sent::add).create(null), Runnable::run);
        try {
            m.askModel("红烧肉", "加我 13812345678 或 wx_oAbc1234 领菜谱");
        } catch (RuntimeException ignored) {
            // 模拟传输返回 {}，解析结果不重要
        }
        assertThat(sent).isNotEmpty();
        String user = sent.get(0).messages().get(1).content();
        assertThat(user).doesNotContain("13812345678", "wx_oAbc1234").contains("[手机号已隐藏]", "[账号标识已隐藏]");
    }
}
