package com.yanhuo.xsd.modules.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanhuo.xsd.common.BizException;
import com.yanhuo.xsd.modules.ai.AiClient;
import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import com.yanhuo.xsd.modules.ai.dto.MenuRecommendRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;
import com.yanhuo.xsd.modules.nutrition.IngredientNutrition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 真 AI 客户端：OpenAI 兼容协议（{@code yanhuo.ai.provider=deepseek} 时启用，默认）。
 *
 * <p>POST {@code <base-url>/chat/completions}，Header {@code Authorization: Bearer <key>}，
 * Body 含 {@code response_format:{type:json_object}}，解析 {@code choices[0].message.content}（JSON 字符串）。
 *
 * <p>营养补全：每 100g 6 项指标（calorie/protein/fat/carb/sugar/gi）→ metricId 1..6。
 * 菜单推荐：基于约束/预算给出推荐组（当前 AiService.recommendMenu 走确定性 MenuRecommender，
 * 本方法为接口契约与未来扩展保留；收到的 req 仅含约束/预算，无候选菜上下文）。
 *
 * <p>降级：key 空 / 网络 / 解析失败时，fallback 调 {@link MockAiClient}，保证基本可用。
 * DeepSeek 偶尔返回带 ```json 围栏的 markdown，解析前清洗。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "yanhuo.ai.provider", havingValue = "deepseek", matchIfMissing = false)
public class DeepSeekAiClient implements AiClient {

    private static final String SOURCE = "deepseek";

    private final RestClient restClient;
    private final MockAiClient mockFallback;
    private final ObjectMapper objectMapper;

    @Value("${yanhuo.ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${yanhuo.ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${yanhuo.ai.deepseek.key:}")
    private String key;

    /** 生产构造：自建 RestClient。 */
    public DeepSeekAiClient(MockAiClient mockFallback, ObjectMapper objectMapper) {
        this(RestClient.builder().build(), mockFallback, objectMapper);
    }

    /** 测试构造：注入 mock RestClient。 */
    public DeepSeekAiClient(RestClient restClient, MockAiClient mockFallback, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.mockFallback = mockFallback;
        this.objectMapper = objectMapper;
    }

    // ---------------- 营养补全 ----------------

    @Override
    public NutritionFillResponse fillNutrition(NutritionFillRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new BizException("食材名不能为空");
        }
        try {
            if (key == null || key.isBlank()) {
                throw new BizException("API_KEY 未配置，降级 mock");
            }
            String content = chat(
                    "你是营养师。给定食材名，返回它每100克的营养成分 JSON，字段 calorie/protein/fat/carb/sugar/gi"
                            + "（数值，gi 是升糖指数 0-100）。只返回 JSON。",
                    "食材名：" + req.name());
            JsonNode node = parseJson(content);
            return new NutritionFillResponse(toNutritionList(node), SOURCE);
        } catch (Exception e) {
            log.warn("DeepSeek fillNutrition 失败，降级 mock: {}", e.getMessage());
            return mockFallback.fillNutrition(req);
        }
    }

    // ---------------- 菜单推荐 ----------------

    @Override
    public List<MenuCandidate> recommendMenu(MenuRecommendRequest req) {
        try {
            if (key == null || key.isBlank()) {
                throw new BizException("API_KEY 未配置，降级 mock");
            }
            String user = String.format(
                    "预算：%s，范围：%s，菜系：%s，标签：%s，分类：%s，最大耗时：%s 分钟，最大难度：%s",
                    req.budget(), req.scope(), req.cuisineIds(), req.tagIds(),
                    req.categoryIds(), req.maxMinutes(), req.maxDifficulty());
            String content = chat(
                    "你是家庭菜单推荐助手。根据健康约束/预算/口味，推荐菜单组合，"
                            + "返回 JSON 数组 [{dishes:[{name,price}],reasons:[...]}]。只返回 JSON。",
                    user);
            JsonNode arr = parseJson(content);
            List<MenuCandidate> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode g : arr) {
                    List<MenuCandidate.DishItem> dishes = new ArrayList<>();
                    JsonNode dishesNode = g.path("dishes");
                    if (dishesNode.isArray()) {
                        for (JsonNode d : dishesNode) {
                            dishes.add(new MenuCandidate.DishItem(
                                    null,
                                    d.path("name").asText(""),
                                    BigDecimal.ONE,
                                    toBd(d.path("price"))));
                        }
                    }
                    List<String> reasons = new ArrayList<>();
                    JsonNode reasonsNode = g.path("reasons");
                    if (reasonsNode.isArray()) {
                        for (JsonNode r : reasonsNode) reasons.add(r.asText());
                    }
                    BigDecimal total = dishes.stream()
                            .map(MenuCandidate.DishItem::price)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    out.add(new MenuCandidate(dishes, total, Map.of(), 0.0, reasons, SOURCE));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("DeepSeek recommendMenu 失败，降级 mock: {}", e.getMessage());
            return mockFallback.recommendMenu(req);
        }
    }

    @Override
    public String provider() {
        return SOURCE;
    }

    // ---------------- 内部：HTTP + 解析 ----------------

    /** POST chat/completions，返回 choices[0].message.content 文本。 */
    private String chat(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "response_format", Map.of("type", "json_object"));
        JsonNode resp = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (resp == null) {
            throw new BizException("DeepSeek 返回空响应");
        }
        String content = resp.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BizException("DeepSeek 返回 content 为空");
        }
        return content;
    }

    /** 清洗 markdown 围栏（```json ... ```）后解析。 */
    private JsonNode parseJson(String raw) throws Exception {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        return objectMapper.readTree(s);
    }

    /** {calorie,protein,fat,carb,sugar,gi} -> List<IngredientNutrition>(metricId 1..6)。 */
    private static List<IngredientNutrition> toNutritionList(JsonNode node) {
        List<Map.Entry<String, Long>> fields = List.of(
                Map.entry("calorie", 1L), Map.entry("protein", 2L), Map.entry("fat", 3L),
                Map.entry("carb", 4L), Map.entry("sugar", 5L), Map.entry("gi", 6L));
        List<IngredientNutrition> list = new ArrayList<>(6);
        for (Map.Entry<String, Long> f : fields) {
            IngredientNutrition n = new IngredientNutrition();
            n.setMetricId(f.getValue());
            n.setValue(toBd(node.path(f.getKey())));
            list.add(n);
        }
        return list;
    }

    private static BigDecimal toBd(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return BigDecimal.ZERO;
        if (n.isNumber()) return n.decimalValue();
        try { return new BigDecimal(n.asText().trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
