package com.gudu.xsd.modules.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * DeepSeekAiClient 测试：mock RestClient stub DeepSeek 响应，断言 JSON 解析/降级。
 * 不真调网络（真调验证为手动）。
 *
 * <p>通过 {@code ReflectionTestUtils} 注入 key/baseUrl/model（绕过 @Value）。
 * RestClient 用 Mockito mock，stub {@code choices[0].message.content}。
 */
class DeepSeekAiClientTest {

    private MockAiClient mockFallback;
    private MenuRecommender menuRecommender;
    private ObjectMapper objectMapper;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private DeepSeekAiClient client;

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

        client = new DeepSeekAiClient(restClient, mockFallback, menuRecommender, objectMapper,
                new com.gudu.xsd.modules.ai.AiOutputGuard());
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

    @Test
    void 护栏_模型拒答无关内容_降级mock() throws Exception {
        // 即使绕过输入预检，模型按角色锁定返回拒答非 JSON 文本 → parseJson 抛错 → 降级 mock
        stubContent("我只能回答食物和营养相关的问题。");
        var mockResp = new NutritionFillResponse(java.util.List.of(), "mock");
        when(mockFallback.fillNutrition(any())).thenReturn(mockResp);
        var r = client.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
    }

    // ---------------- 菜单推荐（候选上下文 → DeepSeek 选菜）----------------

    private static CandidateDish cd(long id, String name, String price) {
        return new CandidateDish(id, name, new BigDecimal(price),
                Map.of(2L, new BigDecimal("10")), List.of());
    }

    private MenuRecommendRequest reqWithCandidates(List<CandidateDish> cands) {
        return new MenuRecommendRequest(
                1L, new BigDecimal("100"), "DAY",
                null, null, null, null, null,
                cands, Map.of("sugarMax", new BigDecimal("25")));
    }

    @Test
    void 菜单推荐_从候选选菜_dishId真实映射() throws Exception {
        var cands = List.of(cd(101, "番茄炒蛋", "10"), cd(102, "黄瓜炒肉", "12"));
        stubContent("{\"menus\":[{\"dishes\":[{\"dishId\":101,\"name\":\"番茄炒蛋\"}],"
                + "\"reasons\":[\"清淡\"]}]}");
        var out = client.recommendMenu(reqWithCandidates(cands));
        assertThat(out).hasSize(1);
        var g = out.get(0);
        assertThat(g.source()).isEqualTo("deepseek");
        assertThat(g.dishes()).hasSize(1);
        // dishId 必须是候选里的真实 id，不是 null
        assertThat(g.dishes().get(0).dishId()).isEqualTo(101L);
        assertThat(g.dishes().get(0).name()).isEqualTo("番茄炒蛋");
        assertThat(g.dishes().get(0).price()).isEqualByComparingTo("10");
        assertThat(g.totalPrice()).isEqualByComparingTo("10");
        assertThat(g.reasons()).contains("清淡");
        verifyNoInteractions(menuRecommender);
    }

    @Test
    void 菜单推荐_LLM编造dishId_跳过非法项() throws Exception {
        var cands = List.of(cd(101, "番茄炒蛋", "10"));
        // LLM 给了候选里没有的 dishId 999
        stubContent("{\"menus\":[{\"dishes\":[{\"dishId\":999,\"name\":\"编造菜\"}]}]}");
        var out = client.recommendMenu(reqWithCandidates(cands));
        // 整组无有效菜 → 抛错降级规则（mock 返回空）
        verify(menuRecommender).recommend(any(), any(), any(), any(), anyString(), anyLong());
    }

    @Test
    void 菜单推荐_网络失败_降级规则() {
        when(responseSpec.body(JsonNode.class)).thenThrow(new RuntimeException("connect refused"));
        var cands = List.of(cd(101, "番茄炒蛋", "10"));
        when(menuRecommender.recommend(any(), any(), any(), any(), anyString(), anyLong()))
                .thenReturn(List.of());
        var out = client.recommendMenu(reqWithCandidates(cands));
        assertThat(out).isEmpty();
        verify(menuRecommender).recommend(any(), any(), any(), any(), anyString(), anyLong());
    }

    @Test
    void 菜单推荐_key空_降级规则() {
        ReflectionTestUtils.setField(client, "key", "");
        var cands = List.of(cd(101, "番茄炒蛋", "10"));
        when(menuRecommender.recommend(any(), any(), any(), any(), anyString(), anyLong()))
                .thenReturn(List.of());
        var out = client.recommendMenu(reqWithCandidates(cands));
        assertThat(out).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void 菜单推荐_无候选上下文_直接走规则() {
        var req = new MenuRecommendRequest(1L, new BigDecimal("100"), "DAY",
                null, null, null, null, null);
        when(menuRecommender.recommend(any(), any(), any(), any(), anyString(), anyLong()))
                .thenReturn(List.of());
        var out = client.recommendMenu(req);
        assertThat(out).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void 菜单推荐_候选数超限_截断到上限() throws Exception {
        // 造 30 个候选，应被截到 18
        java.util.List<CandidateDish> cands = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            cands.add(cd(i, "菜" + i, "5"));
        }
        // LLM 选 dishId=1（在截断范围内）
        stubContent("{\"menus\":[{\"dishes\":[{\"dishId\":1,\"name\":\"菜1\"}],\"reasons\":[]}]}");
        var out = client.recommendMenu(reqWithCandidates(cands));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).dishes().get(0).dishId()).isEqualTo(1L);
    }

    @Test
    void 菜单推荐_WEEK_scope_至多3组() throws Exception {
        var cands = List.of(cd(1, "菜1", "5"), cd(2, "菜2", "5"), cd(3, "菜3", "5"),
                cd(4, "菜4", "5"));
        // LLM 返回 5 组，应被限量到 3
        stubContent("{\"menus\":[" +
                "{\"dishes\":[{\"dishId\":1,\"name\":\"菜1\"}],\"reasons\":[]}," +
                "{\"dishes\":[{\"dishId\":2,\"name\":\"菜2\"}],\"reasons\":[]}," +
                "{\"dishes\":[{\"dishId\":3,\"name\":\"菜3\"}],\"reasons\":[]}," +
                "{\"dishes\":[{\"dishId\":4,\"name\":\"菜4\"}],\"reasons\":[]}," +
                "{\"dishes\":[{\"dishId\":1,\"name\":\"菜1\"}],\"reasons\":[]}]}");
        var req = new MenuRecommendRequest(1L, new BigDecimal("100"), "WEEK",
                null, null, null, null, null, cands, Map.of());
        var out = client.recommendMenu(req);
        assertThat(out).hasSizeLessThanOrEqualTo(3);
    }

    // ---------------- 菜品/一餐营养估算 ----------------

    @Test
    void 菜品估算_解析deepseek返回() throws Exception {
        stubContent("{\"calorie\":350,\"protein\":18,\"fat\":12,\"carb\":20,\"sugar\":6,"
                + "\"note\":\"按2蛋2番茄家常份量估算\"}");
        var r = client.estimateDish(new DishEstimateRequest("一盘番茄炒蛋,2个鸡蛋2个番茄", null));
        assertThat(r.source()).isEqualTo("deepseek");
        assertThat(r.description()).isEqualTo("一盘番茄炒蛋,2个鸡蛋2个番茄");
        assertThat(r.nutrition()).hasSize(5);
        assertThat(r.nutrition().get(1L)).isEqualByComparingTo("350");   // calorie
        assertThat(r.nutrition().get(2L)).isEqualByComparingTo("18");    // protein
        assertThat(r.nutrition().get(5L)).isEqualByComparingTo("6");     // sugar
        assertThat(r.aiNote()).contains("番茄");
        verifyNoInteractions(mockFallback);
    }

    @Test
    void 菜品估算_servingFactor缩放() throws Exception {
        stubContent("{\"calorie\":400,\"protein\":20,\"fat\":10,\"carb\":40,\"sugar\":5,"
                + "\"note\":\"一份\"}");
        var r = client.estimateDish(
                new DishEstimateRequest("一碗牛肉面", new BigDecimal("2")));
        assertThat(r.source()).isEqualTo("deepseek");
        // calorie 400 × 2 = 800
        assertThat(r.nutrition().get(1L)).isEqualByComparingTo("800");
        assertThat(r.nutrition().get(2L)).isEqualByComparingTo("40");
    }

    @Test
    void 菜品估算_key为空_降级mock() {
        ReflectionTestUtils.setField(client, "key", "");
        var mockResp = new DishEstimateResponse("x", Map.of(), "mock", "mock");
        when(mockFallback.estimateDish(any())).thenReturn(mockResp);
        var r = client.estimateDish(new DishEstimateRequest("一碗牛肉面", null));
        assertThat(r.source()).isEqualTo("mock");
        verifyNoInteractions(restClient);
    }

    @Test
    void 菜品估算_网络失败_降级mock() {
        when(responseSpec.body(JsonNode.class)).thenThrow(new RuntimeException("connect refused"));
        var mockResp = new DishEstimateResponse("x", Map.of(), "mock", "mock");
        when(mockFallback.estimateDish(any())).thenReturn(mockResp);
        var r = client.estimateDish(new DishEstimateRequest("一碗牛肉面", null));
        assertThat(r.source()).isEqualTo("mock");
    }

    @Test
    void 菜品估算_描述为空_抛BizException() {
        assertThatThrownBy(() -> client.estimateDish(new DishEstimateRequest("", null)))
                .isInstanceOf(com.gudu.xsd.common.BizException.class);
        verifyNoInteractions(restClient);
    }
}
