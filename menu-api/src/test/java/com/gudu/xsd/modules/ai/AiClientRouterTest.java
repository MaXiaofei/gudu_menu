package com.gudu.xsd.modules.ai;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillResponse;
import com.gudu.xsd.modules.ai.impl.DeepSeekAiClient;
import com.gudu.xsd.modules.ai.impl.GlmAiClient;
import com.gudu.xsd.modules.ai.impl.MockAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link AiClientRouter} 测试：验证运行时委托 + Redis 持久化 + 启动初值回退 + ready 判断。
 *
 * <p>三个 client + StringRedisTemplate 全 mock，不依赖 Spring 容器。
 */
class AiClientRouterTest {

    private DeepSeekAiClient deepSeek;
    private GlmAiClient glm;
    private MockAiClient mock;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        deepSeek = mock(DeepSeekAiClient.class);
        glm = mock(GlmAiClient.class);
        mock = mock(MockAiClient.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    private AiClientRouter router(String bootProvider) {
        return new AiClientRouter(deepSeek, glm, mock, redis, new com.fasterxml.jackson.databind.ObjectMapper(), bootProvider);
    }

    // ---------------- currentProvider：Redis 优先，回退启动初值 ----------------

    @Test
    void 无Redis值_回退启动初值() {
        when(valueOps.get(anyString())).thenReturn(null);
        assertThat(router("glm").currentProvider()).isEqualTo("glm");
    }

    @Test
    void 有Redis值_优先Redis() {
        when(valueOps.get(AiClientRouter.REDIS_KEY)).thenReturn("deepseek");
        // 启动初值是 glm，但 Redis 是 deepseek → 取 deepseek
        assertThat(router("glm").currentProvider()).isEqualTo("deepseek");
    }

    @Test
    void Redis脏值_回退启动初值() {
        when(valueOps.get(AiClientRouter.REDIS_KEY)).thenReturn("garbage");
        assertThat(router("deepseek").currentProvider()).isEqualTo("deepseek");
    }

    @Test
    void 启动初值非法_回退默认deepseek() {
        when(valueOps.get(anyString())).thenReturn(null);
        assertThat(router("nonsense").currentProvider()).isEqualTo("deepseek");
    }

    @Test
    void Redis读取异常_降级启动初值不抛() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(router("glm").currentProvider()).isEqualTo("glm");
    }

    // ---------------- switchProvider ----------------

    @Test
    void 切换合法值_写Redis生效() {
        when(valueOps.get(AiClientRouter.REDIS_KEY)).thenReturn("glm");
        var r = router("deepseek");
        assertThat(r.switchProvider("glm")).isEqualTo("glm");
        verify(valueOps).set(AiClientRouter.REDIS_KEY, "glm");
        assertThat(r.currentProvider()).isEqualTo("glm");
    }

    @Test
    void 切换非法值_抛BizException() {
        var r = router("deepseek");
        assertThatThrownBy(() -> r.switchProvider("claude"))
                .isInstanceOf(BizException.class);
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    void 切换空值_抛BizException() {
        assertThatThrownBy(() -> router("deepseek").switchProvider(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 切换时Redis写入异常_抛BizException() {
        doThrow(new RuntimeException("redis down")).when(valueOps).set(anyString(), anyString());
        assertThatThrownBy(() -> router("deepseek").switchProvider("glm"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Redis");
    }

    // ---------------- 委托：当前 provider 决定调哪个 client ----------------

    @Test
    void 委托_当前deepseek调DeepSeekAiClient() {
        when(valueOps.get(anyString())).thenReturn("deepseek");
        var resp = new NutritionFillResponse(List.of(), "deepseek");
        when(deepSeek.fillNutrition(any())).thenReturn(resp);
        var r = router("glm").fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r).isSameAs(resp);
        verify(deepSeek).fillNutrition(any());
        verifyNoInteractions(glm, mock);
    }

    @Test
    void 委托_切换到glm后调GlmAiClient() {
        var r = router("deepseek");
        // 切到 glm
        when(valueOps.get(anyString())).thenReturn("glm");
        var resp = new NutritionFillResponse(List.of(), "glm");
        when(glm.fillNutrition(any())).thenReturn(resp);
        r.switchProvider("glm");
        var got = r.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(got).isSameAs(resp);
        verify(glm).fillNutrition(any());
        verifyNoInteractions(deepSeek, mock);
    }

    @Test
    void 委托_当前mock调MockAiClient() {
        when(valueOps.get(anyString())).thenReturn("mock");
        var resp = new NutritionFillResponse(List.of(), "mock");
        when(mock.fillNutrition(any())).thenReturn(resp);
        var r = router("deepseek").fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
        verifyNoInteractions(deepSeek, glm);
    }

    // ---------------- providerReady ----------------

    @Test
    void ready_deepseek视key是否配() {
        when(deepSeek.getKey()).thenReturn("sk-xxx");
        when(glm.getKey()).thenReturn("");
        var r = router("deepseek");
        assertThat(r.providerReady("deepseek")).isTrue();
        assertThat(r.providerReady("glm")).isFalse();
        assertThat(r.providerReady("mock")).isTrue();
    }

    @Test
    void PROVIDERS包含全部三个() {
        assertThat(AiClientRouter.PROVIDERS).containsExactly("deepseek", "glm", "mock");
    }
}
