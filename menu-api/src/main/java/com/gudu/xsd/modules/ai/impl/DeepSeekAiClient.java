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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 真 AI 客户端：OpenAI 兼容协议（{@code yanhuo.ai.provider=deepseek} 时启用，默认）。
 *
 * <p>POST {@code <base-url>/chat/completions}，Header {@code Authorization: Bearer <key>}，
 * Body 含 {@code response_format:{type:json_object}}，解析 {@code choices[0].message.content}（JSON 字符串）。
 *
 * <p>营养补全：每 100g 6 项指标（calorie/protein/fat/carb/sugar/gi）→ metricId 1..6。
 * 菜单推荐：AiService 已查好候选菜池 + 健康约束回填进 req.candidates/healthConstraints，
 * 本方法把候选菜上下文序列化进 prompt，让 DeepSeek 从候选里选菜组合并给理由，
 * 解析回真实 dishId（仅候选映射，不编造）；失败（key/网络/解析）降级 {@link MenuRecommender}（规则）。
 *
 * <p>降级：菜单推荐降级复用规则 {@link MenuRecommender}（候选上下文已有，直接过滤/打分/组合）；
 * 营养补全降级 {@link MockAiClient}。DeepSeek 偶尔返回带 ```json 围栏的 markdown，解析前清洗。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "yanhuo.ai.provider", havingValue = "deepseek", matchIfMissing = false)
public class DeepSeekAiClient implements AiClient {

    private static final String SOURCE = "deepseek";
    /** 传给 LLM 的候选菜上限（避免 token 爆）。 */
    private static final int MAX_CANDIDATES = 18;

    private final RestClient restClient;
    private final MockAiClient mockFallback;
    private final MenuRecommender menuRecommender;
    private final ObjectMapper objectMapper;
    private final AiOutputGuard outputGuard;

    /**
     * 系统 prompt 角色锁定段：所有 AI 方法 system prompt 前拼接，把模型职责严格限定在食物营养范围。
     * 超范围请求（政治/暴力/色情/编程/闲聊等）一律回固定拒答语，不解释、不尝试回答。
     */
    static final String ROLE_LOCK =
            "你是「咕嘟小食单」的营养师助手。你的职责严格限定在：食物营养、菜品营养估算、菜单推荐、食材营养补全。"
                    + "对于任何超出食物营养范围的请求（包括但不限于：政治、暴力、违法犯罪、色情、编程、写文章、写诗、"
                    + "闲聊、个人隐私询问），你必须拒绝，只回复：\"我只能回答食物和营养相关的问题。\" "
                    + "不要尝试回答无关问题，不要解释原因。\n";

    @Value("${gudu.ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${gudu.ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${gudu.ai.deepseek.key:}")
    private String key;

    /** 生产构造：自建 RestClient（Spring 装配用，多构造时 @Autowired 显式指定）。 */
    @Autowired
    public DeepSeekAiClient(MockAiClient mockFallback, MenuRecommender menuRecommender,
                            ObjectMapper objectMapper, AiOutputGuard outputGuard) {
        this(RestClient.builder().build(), mockFallback, menuRecommender, objectMapper, outputGuard);
    }

    /** 测试构造：注入 mock RestClient。 */
    public DeepSeekAiClient(RestClient restClient, MockAiClient mockFallback,
                            MenuRecommender menuRecommender, ObjectMapper objectMapper,
                            AiOutputGuard outputGuard) {
        this.restClient = restClient;
        this.mockFallback = mockFallback;
        this.menuRecommender = menuRecommender;
        this.objectMapper = objectMapper;
        this.outputGuard = outputGuard;
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
                    ROLE_LOCK + "你是营养师。给定食材名，返回它每100克的营养成分 JSON，字段 calorie/protein/fat/carb/sugar/gi"
                            + "（数值，gi 是升糖指数 0-100）。只返回 JSON。",
                    "食材名：" + req.name());
            JsonNode node = parseJson(content);
            // 若模型拒答（返回「我只能回答...」等非 JSON 文本），parseJson 抛错 → 降级 mock
            List<IngredientNutrition> list = toNutritionList(node);
            // 输出护栏：per100g 范围校验 + clamp，防离谱值；异常抛 BizException → 降级 mock
            Map<Long, BigDecimal> validated = outputGuard.validateNutrition(toMap(list), true);
            applyValues(list, validated);
            return new NutritionFillResponse(list, SOURCE);
        } catch (Exception e) {
            log.warn("DeepSeek fillNutrition 失败，降级 mock: {}", e.getMessage());
            return mockFallback.fillNutrition(req);
        }
    }

    // ---------------- 菜单推荐 ----------------

    @Override
    public List<MenuCandidate> recommendMenu(MenuRecommendRequest req) {
        // 无候选上下文 → 无法让 LLM 从候选选，直接走规则降级（候选组装是 AiService 的职责）。
        if (req.candidates() == null || req.candidates().isEmpty()) {
            return ruleFallback(req);
        }
        try {
            if (key == null || key.isBlank()) {
                throw new BizException("API_KEY 未配置，降级规则");
            }
            // 候选限量，避免 token 爆；保留 dishId -> 候选 的映射以解析回真实 id。
            List<CandidateDish> limited = req.candidates().size() > MAX_CANDIDATES
                    ? req.candidates().subList(0, MAX_CANDIDATES)
                    : req.candidates();
            Map<Long, CandidateDish> byId = new HashMap<>();
            for (CandidateDish c : limited) {
                byId.put(c.dishId(), c);
            }

            String user = buildUserPrompt(limited, req);
            String content = chat(
                    ROLE_LOCK + "你是家庭菜单推荐助手。根据健康约束/预算/口味，从给定的候选菜里选菜组成菜单，"
                            + "返回 JSON 对象 {\"menus\":[{\"dishes\":[{\"dishId\":数字,\"name\":\"菜名\"}],"
                            + "\"reasons\":[\"...\"]}]}。"
                            + "只能从候选菜里选，dishId 必须用候选里给出的真实数字，不编造。"
                            + "scope 为 DAY 时返回 1 组菜单，WEEK 时返回至多 3 组。只返回 JSON。",
                    user);
            JsonNode root = parseJson(content);
            // 兼容：根可能是数组也可能是 {menus:[...]}
            JsonNode arr = root.isArray() ? root : root.path("menus");
            if (!arr.isArray()) {
                throw new BizException("DeepSeek 返回非数组");
            }

            int limit = "WEEK".equalsIgnoreCase(req.scope()) ? 3 : 1;
            List<MenuCandidate> out = new ArrayList<>();
            for (JsonNode g : arr) {
                if (out.size() >= limit) break;
                List<MenuCandidate.DishItem> dishes = new ArrayList<>();
                Map<Long, BigDecimal> nut = new HashMap<>();
                JsonNode dishesNode = g.path("dishes");
                if (dishesNode.isArray()) {
                    for (JsonNode d : dishesNode) {
                        long dishId = d.path("dishId").asLong(0L);
                        CandidateDish c = byId.get(dishId);
                        // LLM 编造的 dishId（不在候选里）→ 跳过，防止返回不存在的菜。
                        if (c == null) {
                            // 次选：按名字匹配候选（LLM 有时会改名）
                            String nm = d.path("name").asText("");
                            c = byId.values().stream()
                                    .filter(x -> nm.equals(x.name()))
                                    .findFirst().orElse(null);
                        }
                        if (c == null) continue;
                        dishes.add(new MenuCandidate.DishItem(
                                c.dishId(), c.name(), BigDecimal.ONE, c.price()));
                        if (c.nutrition() != null) {
                            for (var e : c.nutrition().entrySet()) {
                                nut.merge(e.getKey(), e.getValue(), BigDecimal::add);
                            }
                        }
                    }
                }
                if (dishes.isEmpty()) continue; // 该组全部不在候选，丢弃
                List<String> reasons = new ArrayList<>();
                JsonNode reasonsNode = g.path("reasons");
                if (reasonsNode.isArray()) {
                    for (JsonNode r : reasonsNode) reasons.add(r.asText());
                }
                BigDecimal total = dishes.stream()
                        .map(MenuCandidate.DishItem::price)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                out.add(new MenuCandidate(dishes, total, nut, 0.0, reasons, SOURCE));
            }
            // LLM 一组没选出来（全编造/全超预算）→ 降级规则
            if (out.isEmpty()) {
                throw new BizException("DeepSeek 未选出有效候选，降级规则");
            }
            return out;
        } catch (Exception e) {
            log.warn("DeepSeek recommendMenu 失败，降级规则: {}", e.getMessage());
            return ruleFallback(req);
        }
    }

    // ---------------- 菜品/一餐营养估算（V2 方案2：纯文本描述） ----------------

    @Override
    public DishEstimateResponse estimateDish(DishEstimateRequest req) {
        if (req.description() == null || req.description().isBlank()) {
            throw new BizException("菜品描述不能为空");
        }
        try {
            if (key == null || key.isBlank()) {
                throw new BizException("API_KEY 未配置，降级 mock");
            }
            BigDecimal factor = req.servingFactor() == null ? BigDecimal.ONE : req.servingFactor();
            String user = "菜品/一餐描述：" + req.description()
                    + "\n份数(servingFactor)：" + factor;
            String content = chat(
                    ROLE_LOCK + "你是营养师。根据用户对一道菜/一餐的文字描述，估算其总营养成分。"
                            + "返回 JSON:{\"calorie\":数值,\"protein\":数值,\"fat\":数值,"
                            + "\"carb\":数值,\"sugar\":数值,\"note\":\"简要说明估算依据\"}。"
                            + "数值是该餐总量(不是per100g)，已考虑用户给的份数。只返回JSON。",
                    user);
            JsonNode node = parseJson(content);
            // metricId 1cal/2protein/3fat/4carb/5sugar；gi 不适用整体餐，跳过
            Map<Long, BigDecimal> nutrition = new LinkedHashMap<>();
            nutrition.put(1L, scale(toBd(node.path("calorie")), factor));
            nutrition.put(2L, scale(toBd(node.path("protein")), factor));
            nutrition.put(3L, scale(toBd(node.path("fat")), factor));
            nutrition.put(4L, scale(toBd(node.path("carb")), factor));
            nutrition.put(5L, scale(toBd(node.path("sugar")), factor));
            // 输出护栏：整餐范围校验 + clamp，防离谱值；异常抛 BizException → 降级 mock
            nutrition = outputGuard.validateNutrition(nutrition, false);
            String note = node.path("note").asText("估算基于常见份量,仅供参考");
            return new DishEstimateResponse(req.description(), nutrition, SOURCE, note);
        } catch (Exception e) {
            log.warn("DeepSeek estimateDish 失败，降级 mock: {}", e.getMessage());
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

    /** 规则降级：用 MenuRecommender 在候选池上过滤/打分/组合。 */
    private List<MenuCandidate> ruleFallback(MenuRecommendRequest req) {
        List<MenuRecommender.CandidateDish> list = new ArrayList<>();
        if (req.candidates() != null) {
            for (CandidateDish c : req.candidates()) {
                list.add(new MenuRecommender.CandidateDish(
                        c.dishId(), c.name(), c.price(), c.nutrition(), c.ingredientNames()));
            }
        }
        Map<String, Object> hc = req.healthConstraints() == null
                ? Map.of() : req.healthConstraints();
        MenuRecommender.Constraints cons = new MenuRecommender.Constraints(
                toBd(hc.get("sugarMax")), toBd(hc.get("calMax")));
        @SuppressWarnings("unchecked")
        List<String> allergies = hc.get("allergies") instanceof List<?> al
                ? al.stream().map(String::valueOf).toList() : List.of();
        long seed = req.memberId() == null ? 42L : req.memberId();
        return menuRecommender.recommend(list, cons, allergies, req.budget(),
                req.scope() == null ? "DAY" : req.scope(), seed);
    }

    /** 组装 user prompt：候选菜（dishId+名+价格）+ 健康约束 + 预算 + scope。 */
    private static String buildUserPrompt(List<CandidateDish> candidates, MenuRecommendRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("范围(scope)：").append(req.scope() == null ? "DAY" : req.scope()).append('\n');
        sb.append("预算(budget)：").append(req.budget()).append('\n');
        Map<String, Object> hc = req.healthConstraints() == null ? Map.of() : req.healthConstraints();
        sb.append("健康约束：");
        if (!hc.isEmpty()) sb.append(hc); else sb.append("无");
        sb.append('\n');
        sb.append("候选菜（只能从中选，dishId 为真实数字）：\n");
        for (CandidateDish c : candidates) {
            sb.append("- dishId=").append(c.dishId())
                    .append(" 名称=").append(c.name())
                    .append(" 价格=").append(c.price()).append('\n');
        }
        return sb.toString();
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
