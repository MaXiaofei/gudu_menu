package com.gudu.xsd.modules.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.ai.AiClient;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议 AI 客户端基类：DeepSeek / 智谱 GLM 等都走 {@code <base-url>/chat/completions}，
 * Header {@code Authorization: Bearer <key>}，Body 含 {@code response_format:{type:json_object}}，
 * 解析 {@code choices[0].message.content}（JSON 字符串）。协议一致，故共用全部逻辑。
 *
 * <p>子类只需声明 4 个模板点：{@link #baseUrl()}、{@link #model()}、{@link #key()}、{@link #provider()}。
 * 子类各自作为常驻 {@code @Component}（由 {@code AiClientRouter} 在运行时按 provider 选中委托）。
 *
 * <p>营养补全：每 100g 6 项指标（calorie/protein/fat/carb/sugar/gi）→ metricId 1..6。
 * 菜单推荐：AiService 已查好候选菜池 + 健康约束回填进 req.candidates/healthConstraints，
 * 本方法把候选菜上下文序列化进 prompt，让模型从候选里选菜组合并给理由，
 * 解析回真实 dishId（仅候选映射，不编造）；失败（key/网络/解析）降级 {@link MenuRecommender}（规则）。
 *
 * <p>降级：菜单推荐降级复用规则 {@link MenuRecommender}；营养补全/菜品估算降级 {@link MockAiClient}。
 * 模型偶尔返回带 ```json 围栏的 markdown，解析前清洗。
 */
@Slf4j
public abstract class OpenAiCompatibleClient implements AiClient {

    /** 传给 LLM 的候选菜上限（AiService 已用规则引擎预筛选为 Top 5）。 */
    protected static final int MAX_CANDIDATES = 5;

    /**
     * 系统 prompt 角色锁定段：所有 AI 方法 system prompt 前拼接，把模型职责严格限定在食物营养范围。
     * 超范围请求（政治/暴力/色情/编程/闲聊等）一律回固定拒答语，不解释、不尝试回答。
     */
    protected static final String ROLE_LOCK =
            "你是「咕嘟小食单」的营养师助手。你的职责严格限定在：食物营养、菜品营养估算、菜单推荐、食材营养补全。"
                    + "对于任何超出食物营养范围的请求（包括但不限于：政治、暴力、违法犯罪、色情、编程、写文章、写诗、"
                    + "闲聊、个人隐私询问），你必须拒绝，只回复：\"我只能回答食物和营养相关的问题。\" "
                    + "不要尝试回答无关问题，不要解释原因。\n";

    protected final RestClient restClient;
    protected final MockAiClient mockFallback;
    protected final MenuRecommender menuRecommender;
    protected final ObjectMapper objectMapper;
    protected final AiOutputGuard outputGuard;

    /** 生产构造：自建 RestClient。 */
    protected OpenAiCompatibleClient(MockAiClient mockFallback, MenuRecommender menuRecommender,
                                     ObjectMapper objectMapper, AiOutputGuard outputGuard) {
        this(RestClient.builder().build(), mockFallback, menuRecommender, objectMapper, outputGuard);
    }

    /** 测试构造：注入 mock RestClient。 */
    protected OpenAiCompatibleClient(RestClient restClient, MockAiClient mockFallback,
                                     MenuRecommender menuRecommender, ObjectMapper objectMapper,
                                     AiOutputGuard outputGuard) {
        this.restClient = restClient;
        this.mockFallback = mockFallback;
        this.menuRecommender = menuRecommender;
        this.objectMapper = objectMapper;
        this.outputGuard = outputGuard;
    }

    /** 4 个模板点：由子类提供厂商差异。 */
    protected abstract String baseUrl();

    protected abstract String model();

    protected abstract String key();

    // ---------------- 营养补全 ----------------

    @Override
    public NutritionFillResponse fillNutrition(NutritionFillRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new BizException("食材名不能为空");
        }
        try {
            String k = key();
            if (k == null || k.isBlank()) {
                throw new BizException("API_KEY 未配置，降级 mock");
            }
            ChatResult cr = chat(
                    ROLE_LOCK + "你是营养师。给定食材名，返回它每100克的营养成分 JSON，字段 calorie/protein/fat/carb/sugar/gi"
                            + "（数值，gi 是升糖指数 0-100）。只返回 JSON。",
                    "食材名：" + req.name());
            JsonNode node = parseJson(cr.content);
            List<IngredientNutrition> list = toNutritionList(node);
            Map<Long, BigDecimal> validated = outputGuard.validateNutrition(toMap(list), true);
            applyValues(list, validated);
            return new NutritionFillResponse(list, provider(), cr.tokensIn, cr.tokensOut);
        } catch (Exception e) {
            log.warn("{} fillNutrition 失败，降级 mock: {}", provider(), e.getMessage());
            return mockFallback.fillNutrition(req);
        }
    }

    // ---------------- 菜品/一餐营养估算（纯文本描述） ----------------

    @Override
    public DishEstimateResponse estimateDish(DishEstimateRequest req) {
        if (req.description() == null || req.description().isBlank()) {
            throw new BizException("菜品描述不能为空");
        }
        try {
            String k = key();
            if (k == null || k.isBlank()) {
                throw new BizException("API_KEY 未配置，降级 mock");
            }
            BigDecimal factor = req.servingFactor() == null ? BigDecimal.ONE : req.servingFactor();
            String user = "菜品/一餐描述：" + req.description()
                    + "\n份数(servingFactor)：" + factor;
            ChatResult cr = chat(
                    ROLE_LOCK + "你是营养师。根据用户对一道菜/一餐的文字描述，估算其总营养成分。"
                            + "返回 JSON:{\"calorie\":数值,\"protein\":数值,\"fat\":数值,"
                            + "\"carb\":数值,\"sugar\":数值}。"
                            + "数值是该餐总量(不是per100g)，已考虑用户给的份数。"
                            + "只返回 JSON，禁止任何解释、建议、问候语、额外文字。",
                    user);
            JsonNode node = parseJson(cr.content);
            // metricId 1cal/2protein/3fat/4carb/5sugar；gi 不适用整体餐，跳过
            Map<Long, BigDecimal> nutrition = new LinkedHashMap<>();
            nutrition.put(1L, scale(toBd(node.path("calorie")), factor));
            nutrition.put(2L, scale(toBd(node.path("protein")), factor));
            nutrition.put(3L, scale(toBd(node.path("fat")), factor));
            nutrition.put(4L, scale(toBd(node.path("carb")), factor));
            nutrition.put(5L, scale(toBd(node.path("sugar")), factor));
            // 输出护栏：整餐范围校验 + clamp，防离谱值；异常抛 BizException → 降级 mock
            nutrition = outputGuard.validateNutrition(nutrition, false);
            // 透传 LLM 给出的估算说明（如份量假设）；缺省回 "ok" 保持兼容
            String aiNote = node.path("note").asText("ok");
            return new DishEstimateResponse(req.description(), nutrition, provider(), aiNote,
                    cr.tokensIn, cr.tokensOut);
        } catch (Exception e) {
            log.warn("{} estimateDish 失败，降级 mock: {}", provider(), e.getMessage());
            return mockFallback.estimateDish(req);
        }
    }

    /** value × factor（factor=1 时原样返回，保留 AI 给的精度）。 */
    private static BigDecimal scale(BigDecimal value, BigDecimal factor) {
        if (value == null) return BigDecimal.ZERO;
        if (factor == null || factor.compareTo(BigDecimal.ONE) == 0) return value;
        return value.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private record ChatResult(String content, int tokensIn, int tokensOut) {}

    // ---------------- 内部：HTTP + 解析 ----------------

    /** POST chat/completions，返回 content 文本 + token 用量。 */
    private ChatResult chat(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model(),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "response_format", Map.of("type", "json_object"));
        JsonNode resp = restClient.post()
                .uri(baseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + key())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (resp == null) {
            throw new BizException(provider() + " 返回空响应");
        }
        String content = resp.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BizException(provider() + " 返回 content 为空");
        }
        int tin = resp.path("usage").path("prompt_tokens").asInt(0);
        int tout = resp.path("usage").path("completion_tokens").asInt(0);
        return new ChatResult(content, tin, tout);
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

    /** List<IngredientNutrition> -> Map<metricId, value>（供 outputGuard 校验）。 */
    private static Map<Long, BigDecimal> toMap(List<IngredientNutrition> list) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        for (IngredientNutrition n : list) m.put(n.getMetricId(), n.getValue());
        return m;
    }

    /** 把 clamp 后的值写回 list（保持顺序与 metricId 一致）。 */
    private static void applyValues(List<IngredientNutrition> list, Map<Long, BigDecimal> validated) {
        for (IngredientNutrition n : list) {
            BigDecimal v = validated.get(n.getMetricId());
            if (v != null) n.setValue(v);
        }
    }

    private static BigDecimal toBd(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return BigDecimal.ZERO;
        if (n.isNumber()) return n.decimalValue();
        try { return new BigDecimal(n.asText().trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** Object -> BigDecimal（healthConstraints 里的数值可能是 Integer/Double/String）。 */
    private static BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString().trim()); } catch (Exception e) { return null; }
    }
}
