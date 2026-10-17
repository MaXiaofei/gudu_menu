package com.gudu.xsd.modules.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.ai.AiOutputGuard;
import com.gudu.xsd.modules.ai.MenuRecommender;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * DeepSeek 子类测试：验证它继承自 {@link OpenAiCompatibleClient} 且 4 个模板点正确绑定
 * （base-url/model/key/source）。HTTP/解析/护栏/降级/候选映射等共用逻辑的覆盖见
 * {@link OpenAiCompatibleClientTest}（参数化跑 deepseek/glm 两套）。
 */
class DeepSeekAiClientTest {

    @Test
    void 继承基类_且source为deepseek() {
        var c = new DeepSeekAiClient(mock(MockAiClient.class), mock(MenuRecommender.class),
                new ObjectMapper(), new AiOutputGuard());
        assertThat(c).isInstanceOf(OpenAiCompatibleClient.class);
        assertThat(c.provider()).isEqualTo("deepseek");
    }

    @Test
    void 读取配置绑定() {
        RestClient rc = mock(RestClient.class);
        var c = new DeepSeekAiClient(rc, mock(MockAiClient.class), mock(MenuRecommender.class),
                new ObjectMapper(), new AiOutputGuard());
        ReflectionTestUtils.setField(c, "baseUrl", "https://api.deepseek.com/v1");
        ReflectionTestUtils.setField(c, "model", "deepseek-flash");
        ReflectionTestUtils.setField(c, "key", "sk-xxx");
        // 4 模板点经基类委托调用子类实现，值应与注入一致
        assertThat(c.provider()).isEqualTo("deepseek");
        assertThat(c.getKey()).isEqualTo("sk-xxx");
    }

    @Test
    void key未配_getKey为null_视为未就绪() {
        var c = new DeepSeekAiClient(mock(RestClient.class), mock(MockAiClient.class),
                mock(MenuRecommender.class), new ObjectMapper(), new AiOutputGuard());
        // 未经 Spring @Value 注入时为 null（生产环境 @Value 默认给空串，Router 的 hasKey 同样判为未就绪）
        assertThat(c.getKey()).isNull();
    }
}
