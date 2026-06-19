package com.yanhuo.xsd.modules.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;
import com.yanhuo.xsd.modules.nutrition.IngredientNutrition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DeepSeekAiClient 测试：mock RestClient stub DeepSeek 响应，断言 JSON 解析/降级。
 * 不真调网络（真调验证为手动）。
 *
 * <p>通过 {@code ReflectionTestUtils} 注入 key/baseUrl/model（绕过 @Value）。
 * RestClient 用 Mockito mock，stub {@code choices[0].message.content}。
 */
class DeepSeekAiClientTest {

    private MockAiClient mockFallback;
    private ObjectMapper objectMapper;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private DeepSeekAiClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockFallback = mock(MockAiClient.class);
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

        client = new DeepSeekAiClient(restClient, mockFallback, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.deepseek.com/v1");
        ReflectionTestUtils.setField(client, "model", "deepseek-chat");
        ReflectionTestUtils.setField(client, "key", "sk-test-key");
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

    @Test
    void 营养补全_解析deepseek返回() throws Exception {
        stubContent("{\"calorie\":19,\"protein\":0.9,\"fat\":0.2,\"carb\":4.0,\"sugar\":2.6,\"gi\":30}");
        var r = client.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("deepseek");
        assertThat(r.nutrition()).hasSize(6);
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("19");   // calorie
        assertThat(m.get(2L)).isEqualByComparingTo("0.9");  // protein
        assertThat(m.get(6L)).isEqualByComparingTo("30");   // gi
        verifyNoInteractions(mockFallback);
    }

    @Test
    void 营养补全_清洗markdown围栏() throws Exception {
        // DeepSeek 偶尔返回 ```json ... ``` 包裹
        stubContent("```json\n{\"calorie\":143,\"protein\":20.3,\"fat\":6.2,\"carb\":1.5,\"sugar\":0.9,\"gi\":0}\n```");
        var r = client.fillNutrition(new NutritionFillRequest("猪肉", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("143");
        assertThat(m.get(2L)).isEqualByComparingTo("20.3");
        verifyNoInteractions(mockFallback);
    }

    @Test
    void key为空_降级mock() {
        ReflectionTestUtils.setField(client, "key", "");
        var mockResp = new NutritionFillResponse(java.util.List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = client.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
        verifyNoInteractions(restClient);
    }

    @Test
    void 网络失败_降级mock() {
        when(responseSpec.body(JsonNode.class)).thenThrow(new RuntimeException("connect refused"));
        var mockResp = new NutritionFillResponse(java.util.List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = client.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
    }

    @Test
    void content空_降级mock() throws Exception {
        stubContent("");
        var mockResp = new NutritionFillResponse(java.util.List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = client.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
    }
}
