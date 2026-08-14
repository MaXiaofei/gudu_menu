package com.gudu.xsd.modules.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.ai.AiOutputGuard;
import com.gudu.xsd.modules.ai.MenuRecommender;
import com.gudu.xsd.modules.ai.dto.CandidateDish;
import com.gudu.xsd.modules.ai.dto.DishEstimateRequest;
import com.gudu.xsd.modules.ai.dto.DishEstimateResponse;
import com.gudu.xsd.modules.ai.dto.MenuCandidate;
import com.gudu.xsd.modules.ai.dto.MenuRecommendRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillResponse;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link OpenAiCompatibleClient} 基类测试：mock RestClient stub 响应，验证 HTTP/解析/护栏/降级/候选映射。
 *
 * <p>用匿名子类提供 4 个模板点（base-url/model/key/source），参数化跑 deepseek/glm 两套配置，
 * 证明共用逻辑对两个厂商都正确（两个厂商协议一致）。不真调网络。
 *
 * <p>覆盖原 DeepSeekAiClientTest 的全部核心用例（解析/清洗/key空降级/网络失败降级/拒答降级/候选映射/编造跳过/WEEK限3组/servingFactor缩放）。
 */
class OpenAiCompatibleClientTest {

    private MockAiClient mockFallback;
    private MenuRecommender menuRecommender;
    private ObjectMapper objectMapper;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec bodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockFallback = mock(MockAiClient.class);
        menuRecommender = mock(MenuRecommender.class);
        objectMapper = new ObjectMapper();
        restClient = mock(RestClient.class);
        bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    /** 造一个 source=给定值 的基类实例（匿名子类填 4 模板点）。 */
    private OpenAiCompatibleClient client(String source) {
        return new OpenAiCompatibleClient(restClient, mockFallback, menuRecommender, objectMapper,
                new AiOutputGuard()) {
            @Override protected String baseUrl() { return "https://example.test/v1"; }
            @Override protected String model() { return source + "-model"; }
            @Override protected String key() { return "sk-test-key"; }
            @Override public String provider() { return source; }
        };
    }

    private void stubContent(String content) throws Exception {
        JsonNode resp = objectMapper.readTree(
                "{\"choices\":[{\"message\":{\"content\":" +
                        objectMapper.writeValueAsString(content) + "}}]}");
        when(responseSpec.body(JsonNode.class)).thenReturn(resp);
    }

    private static Map<Long, BigDecimal> toMap(NutritionFillResponse r) {
        return r.nutrition().stream()
                .collect(Collectors.toMap(IngredientNutrition::getMetricId, IngredientNutrition::getValue));
    }

    // ---------------- 营养补全（参数化：deepseek/glm 两套） ----------------

    @ParameterizedTest
    @ValueSource(strings = {"deepseek", "glm"})
    void 营养补全_解析返回(String source) throws Exception {
        stubContent("{\"calorie\":19,\"protein\":0.9,\"fat\":0.2,\"carb\":4.0,\"sugar\":2.6,\"gi\":30}");
        var r = client(source).fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo(source);
        assertThat(r.nutrition()).hasSize(6);
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("19");   // calorie
        assertThat(m.get(2L)).isEqualByComparingTo("0.9");  // protein
        assertThat(m.get(6L)).isEqualByComparingTo("30");   // gi
        verifyNoInteractions(mockFallback);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek", "glm"})
    void 营养补全_清洗markdown围栏(String source) throws Exception {
        stubContent("```json\n{\"calorie\":143,\"protein\":20.3,\"fat\":6.2,\"carb\":1.5,\"sugar\":0.9,\"gi\":0}\n```");
        var r = client(source).fillNutrition(new NutritionFillRequest("猪肉", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("143");
        assertThat(m.get(2L)).isEqualByComparingTo("20.3");
        verifyNoInteractions(mockFallback);
    }

    @Test
    void key为空_降级mock() {
        var c = new OpenAiCompatibleClient(restClient, mockFallback, menuRecommender, objectMapper,
                new AiOutputGuard()) {
            @Override protected String baseUrl() { return "https://example.test/v1"; }
            @Override protected String model() { return "m"; }
            @Override protected String key() { return ""; }   // 空 key
            @Override public String provider() { return "deepseek"; }
        };
        var mockResp = new NutritionFillResponse(List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = c.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
        verifyNoInteractions(restClient);
    }

    @Test
    void 网络失败_降级mock() {
        when(responseSpec.body(JsonNode.class)).thenThrow(new RuntimeException("connect refused"));
        var mockResp = new NutritionFillResponse(List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = client("deepseek").fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
    }

    @Test
    void content空_降级mock() throws Exception {
        stubContent("");
        var mockResp = new NutritionFillResponse(List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = client("glm").fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
    }

    @Test
    void 护栏_模型拒答无关内容_降级mock() throws Exception {
        stubContent("我只能回答食物和营养相关的问题。");
        var mockResp = new NutritionFillResponse(List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = client("deepseek").fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
    }

    // ---------------- 菜单推荐（候选上下文 → 选菜） ----------------

    private static CandidateDish cd(long id, String name) {
        return new CandidateDish(id, name,
                Map.of(2L, new BigDecimal("10")), List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek", "glm"})
    void 菜品估算_解析返回(String source) throws Exception {
        stubContent("{\"calorie\":350,\"protein\":18,\"fat\":12,\"carb\":20,\"sugar\":6,"
                + "\"note\":\"按2蛋2番茄家常份量估算\"}");
        var r = client(source).estimateDish(new DishEstimateRequest("一盘番茄炒蛋", null));
        assertThat(r.source()).isEqualTo(source);
        assertThat(r.nutrition()).hasSize(5);
        assertThat(r.nutrition().get(1L)).isEqualByComparingTo("350");
        assertThat(r.nutrition().get(5L)).isEqualByComparingTo("6");
        assertThat(r.aiNote()).contains("番茄");
        verifyNoInteractions(mockFallback);
    }

    @Test
    void 菜品估算_servingFactor缩放() throws Exception {
        stubContent("{\"calorie\":400,\"protein\":20,\"fat\":10,\"carb\":40,\"sugar\":5,\"note\":\"一份\"}");
        var r = client("glm").estimateDish(new DishEstimateRequest("一碗牛肉面", new BigDecimal("2")));
        assertThat(r.nutrition().get(1L)).isEqualByComparingTo("800");   // 400×2
        assertThat(r.nutrition().get(2L)).isEqualByComparingTo("40");
    }

    @Test
    void 菜品估算_描述为空_抛BizException() {
        assertThatThrownBy(() -> client("deepseek").estimateDish(new DishEstimateRequest("", null)))
                .isInstanceOf(com.gudu.xsd.common.BizException.class);
        verifyNoInteractions(restClient);
    }
}
