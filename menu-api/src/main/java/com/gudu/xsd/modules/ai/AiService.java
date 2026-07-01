package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.ai.MenuRecommender.Constraints;
import com.gudu.xsd.modules.ai.dto.CandidateDish;
import com.gudu.xsd.modules.ai.dto.DishEstimateRequest;
import com.gudu.xsd.modules.ai.dto.DishEstimateResponse;
import com.gudu.xsd.modules.ai.dto.MenuCandidate;
import com.gudu.xsd.modules.ai.dto.MenuRecommendRequest;
import com.gudu.xsd.modules.ai.dto.MenuRecommendResponse;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillResponse;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishSearchDTO;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.dish.DishQueryService;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 编排服务：把外部 AI 能力（{@link AiClient}）与本项目 IO（查菜/营养/成员档案、落库、记日志）粘起来。
 *
 * <p>{@code fillNutrition}：调 AiClient 拿 per100g 6 项营养 → 若 ingredientId 非空，
 * 复用 {@link IngredientService#saveWithNutrition} 把营养落到该食材 → 记 ai_call_log。
 *
 * <p>{@code recommendMenu}：取 member.healthProfile(constraints/allergies) → DishService.search 拉候选池 →
 * 各菜 DishQueryService.nutrition + dish_ingredient 食材名 → 组装 CandidateDish →
 * MenuRecommender 过滤/打分/组合（纯函数）→ 记 ai_call_log → 返回候选。
 *
 * <p>日志记录失败不阻断主流程（AI 调用审计是旁路），仅打 warn。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiClient aiClient;
    private final IngredientService ingredientService;
    private final IngredientMapper ingredientMapper;
    private final DishService dishService;
    private final DishQueryService dishQueryService;
    private final DishIngredientMapper dishIngredientMapper;
    private final MemberMapper memberMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final ObjectMapper objectMapper;
    private final AiInputGuard inputGuard;
    private final MenuRecommender menuRecommender;
    private final com.gudu.xsd.modules.dish.mapper.DishMapper dishMapper;
    private final com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper cookingRecordMapper;

    /** 每 member 每日 AI 调用上限（配置 gudu.ai.daily-limit，默认 50）。null/无 member 不限。 */
    @Value("${gudu.ai.daily-limit:50}")
    private int dailyLimit;

    private static final long METRIC_SUGAR = 5L;
    private static final long METRIC_CAL = 1L;

    // ---------------- 营养补全 ----------------

    public NutritionFillResponse fillNutrition(NutritionFillRequest req) {
        long start = System.currentTimeMillis();
        Long memberId = null; // 营养补全场景无 member 维度，预留
        // 输入护栏：食材名合法性（空/过长/黑名单）。无 member → 不限额度。
        inputGuard.validate(req.name());
        java.util.Map<String, Object> reqMap = new java.util.HashMap<>();
        reqMap.put("name", req.name());
        reqMap.put("ingredientId", req.ingredientId());
        String reqJson = safeJson(reqMap);
        try {
            NutritionFillResponse resp = aiClient.fillNutrition(req);
            // 落库
            if (req.ingredientId() != null) {
                Ingredient ing = ingredientService.getById(req.ingredientId());
                if (ing != null) {
                    ingredientService.replaceNutrition(ing.getId(), resp.nutrition());
                }
            }
            logCall("nutrition_fill", memberId, reqJson, safeJson(resp),
                    resp.tokensIn(), resp.tokensOut(), BigDecimal.ZERO, aiClient.provider(), "ok", null, start);
            return resp;
        } catch (BizException e) {
            logCall("nutrition_fill", memberId, reqJson, null,
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "fail", e.getMessage(), start);
            throw e;
        } catch (RuntimeException e) {
            logCall("nutrition_fill", memberId, reqJson, null,
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "fail", e.getMessage(), start);
            throw e;
        }
    }

    // ---------------- 菜单推荐 ----------------

    public List<MenuCandidate> recommendMenu(MenuRecommendRequest req) {
        long start = System.currentTimeMillis();
        String reqJson = safeJson(req);
        // 额度护栏：有 member 维度时，校验今日调用次数（scene 无关，全 AI 接口共用额度）。
        checkDailyLimit(req.memberId());
        try {
            // 1. 取成员健康档案（constraints / allergies）→ healthConstraints
            Constraints cons = new Constraints(null, null);
            List<String> allergies = List.of();
            if (req.memberId() != null) {
                Member m = memberMapper.selectById(req.memberId());
                if (m != null && m.getHealthProfile() != null) {
                    cons = parseConstraints(m.getHealthProfile());
                    allergies = parseAllergies(m.getHealthProfile());
                }
            }
            // 2. 候选池：DishService.search
            DishSearchDTO q = new DishSearchDTO();
            q.setPageNum(1);
            q.setPageSize(50);
            q.setCuisineIds(req.cuisineIds());
            q.setTagIds(req.tagIds());
            q.setCategoryIds(req.categoryIds());
            q.setMaxMinutes(req.maxMinutes());
            q.setMaxDifficulty(req.maxDifficulty());
            List<Dish> dishes = dishService.search(q).getRecords();

            // 3. 各菜组装 CandidateDish：营养(per份) + 食材名
            List<CandidateDish> candidates = new ArrayList<>();
            Map<Long, String> ingNameCache = new HashMap<>();
            for (Dish d : dishes) {
                Map<Long, BigDecimal> nut = dishQueryService.nutrition(d.getId(), BigDecimal.ONE);
                if (nut == null) nut = Map.of();
                List<String> ingNames = ingredientNamesOf(d.getId(), ingNameCache);
                BigDecimal price = d.getPrice() == null ? BigDecimal.ZERO : d.getPrice();
                candidates.add(new CandidateDish(d.getId(), d.getName(), price, nut, ingNames));
            }

            // 3.5 价格缺失检测：如果候选菜全部 price=0，不走 AI，走常做菜推荐
            boolean allPriceZero = candidates.stream().allMatch(c -> c.price().compareTo(BigDecimal.ZERO) == 0);
            if (allPriceZero) {
                List<MenuCandidate> frequent = recommendFrequentDishes(req, cons, allergies);
                if (!frequent.isEmpty()) {
                    logCall("menu_recommend", req.memberId(), reqJson, safeJson(frequent),
                            0, 0, BigDecimal.ZERO, "frequent", "ok", "price missing, frequent dishes", start);
                    return frequent;
                }
                // 常做菜也为空 → 继续走正常流程（预算约束失效但至少有推荐）
            }

            // 4. 规则引擎先跑：过滤/打分/组合，取 Top 5 结果里的菜品作为 LLM 候选
            Map<String, Object> hc = new HashMap<>();
            if (cons.sugarMax() != null) hc.put("sugarMax", cons.sugarMax());
            if (cons.calMax() != null) hc.put("calMax", cons.calMax());
            if (!allergies.isEmpty()) hc.put("allergies", allergies);

            // 组装成 MenuRecommender 候选
            List<MenuRecommender.CandidateDish> rcList = new ArrayList<>();
            for (CandidateDish cd : candidates) {
                rcList.add(new MenuRecommender.CandidateDish(
                        cd.dishId(), cd.name(), cd.price(), cd.nutrition(), cd.ingredientNames()));
            }
            long seed = req.memberId() == null ? 42L : req.memberId();
            final List<MenuCandidate> ruleResult = menuRecommender.recommend(
                    rcList, cons, allergies, req.budget(),
                    req.scope() == null ? "DAY" : req.scope(), seed);

            // 从规则结果中提取唯一菜品，去重后取 top 5
            Set<Long> ruleDishIds = new LinkedHashSet<>();
            for (MenuCandidate mc : ruleResult) {
                for (MenuCandidate.DishItem di : mc.dishes()) {
                    ruleDishIds.add(di.dishId());
                    if (ruleDishIds.size() >= 5) break;
                }
                if (ruleDishIds.size() >= 5) break;
            }

            // 过滤出这 5 个候选
            List<CandidateDish> top5 = new ArrayList<>();
            Map<Long, CandidateDish> byId = candidates.stream()
                    .collect(Collectors.toMap(CandidateDish::dishId, c -> c, (a, b) -> a));
            for (Long id : ruleDishIds) {
                CandidateDish c = byId.get(id);
                if (c != null) top5.add(c);
            }
            if (top5.size() < 3) {
                top5 = candidates.stream().limit(5).collect(Collectors.toList());
            }

            // 5. LLM 从规则预选的 Top 5 中做最终选择 + 生成理由
            MenuRecommendRequest enriched = new MenuRecommendRequest(
                    req.memberId(), req.budget(), req.scope(),
                    req.cuisineIds(), req.tagIds(), req.categoryIds(),
                    req.maxMinutes(), req.maxDifficulty(),
                    top5, hc);
            MenuRecommendResponse mr;
            try {
                mr = aiClient.recommendMenu(enriched);
            } catch (RuntimeException llmErr) {
                // LLM 失败 → 降级规则引擎结果（不抛异常，保证用户拿到推荐）
                log.warn("LLM recommendMenu 失败，降级规则结果: {}", llmErr.getMessage());
                logCall("menu_recommend", req.memberId(), reqJson, null,
                        0, 0, BigDecimal.ZERO, aiClient.provider(), "fail",
                        "llm error, fallback to rule: " + llmErr.getMessage(), start);
                return ruleResult;
            }

            // LLM 返回空 → 也降级规则结果
            if (mr.groups() == null || mr.groups().isEmpty()) {
                logCall("menu_recommend", req.memberId(), reqJson, null,
                        mr.tokensIn(), mr.tokensOut(), BigDecimal.ZERO, aiClient.provider(), "ok",
                        "llm empty, fallback to rule", start);
                return ruleResult;
            }

            logCall("menu_recommend", req.memberId(), reqJson, safeJson(mr.groups()),
                    mr.tokensIn(), mr.tokensOut(), BigDecimal.ZERO, aiClient.provider(), "ok", null, start);
            return mr.groups();
        } catch (BizException e) {
            logCall("menu_recommend", req.memberId(), reqJson, null,
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "fail", e.getMessage(), start);
            throw e;
        } catch (RuntimeException e) {
            logCall("menu_recommend", req.memberId(), reqJson, null,
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "fail", e.getMessage(), start);
            throw e;
        }
    }

    // ---------------- 菜品/一餐营养估算 ----------------

    public DishEstimateResponse estimateDish(DishEstimateRequest req) {
        long start = System.currentTimeMillis();
        Long memberId = null; // 整体餐估算无 member 维度，预留
        // 输入护栏：菜品描述合法性（空/过长/黑名单）。无 member → 不限额度。
        inputGuard.validate(req.description());
        String reqJson = safeJson(req);
        try {
            DishEstimateResponse resp = aiClient.estimateDish(req);
            logCall("dish_estimate", memberId, reqJson, safeJson(resp),
                    resp.tokensIn(), resp.tokensOut(), BigDecimal.ZERO, aiClient.provider(), "ok", null, start);
            return resp;
        } catch (BizException e) {
            logCall("dish_estimate", memberId, reqJson, null,
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "fail", e.getMessage(), start);
            throw e;
        } catch (RuntimeException e) {
            logCall("dish_estimate", memberId, reqJson, null,
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "fail", e.getMessage(), start);
            throw e;
        }
    }

    // ---------------- 辅助 ----------------

    /** 取某菜的食材名列表（用于过敏过滤），带缓存。 */
    private List<String> ingredientNamesOf(Long dishId, Map<Long, String> cache) {
        List<DishIngredient> dis = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", dishId));
        if (dis.isEmpty()) return List.of();
        List<Long> ingIds = dis.stream().map(DishIngredient::getIngredientId).distinct().collect(Collectors.toList());
        List<Ingredient> ings = ingredientMapper.selectList(new QueryWrapper<Ingredient>().in("id", ingIds));
        return ings.stream().map(Ingredient::getName).collect(Collectors.toList());
    }

    /** healthProfile.constraints：{ sugarMax, calMax }（数值）。 */
    @SuppressWarnings("unchecked")
    private Constraints parseConstraints(Map<String, Object> hp) {
        BigDecimal sugarMax = null, calMax = null;
        Object c = hp.get("constraints");
        if (c instanceof Map<?, ?> cm) {
            sugarMax = toBd(cm.get("sugarMax"));
            calMax = toBd(cm.get("calMax"));
        }
        return new Constraints(sugarMax, calMax);
    }

    /** healthProfile.allergies：["花生", ...]（食材名）。 */
    @SuppressWarnings("unchecked")
    private List<String> parseAllergies(Map<String, Object> hp) {
        Object a = hp.get("allergies");
        if (a instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) out.add(o.toString());
            }
            return out;
        }
        return List.of();
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }

    private String safeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    /**
     * 额度护栏：统计该 member 今日(scene 无关)AI 调用次数，超 {@link #dailyLimit} 拒绝。
     * memberId 为 null（如营养补全/菜品估算无 member 维度）时不限制。日志查询失败不阻断主流程。
     */
    private void checkDailyLimit(Long memberId) {
        if (memberId == null || dailyLimit <= 0) return;
        try {
            java.time.LocalDateTime todayStart =
                    java.time.LocalDate.now().atStartOfDay();
            Long count = aiCallLogMapper.selectCount(
                    new QueryWrapper<AiCallLog>()
                            .eq("member_id", memberId)
                            .ge("create_time", todayStart));
            if (count != null && count >= dailyLimit) {
                throw new BizException("今日 AI 调用已达上限（" + dailyLimit + " 次），明天再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            // 额度查询本身失败（DB 抖动等）不阻断，仅 warn
            log.warn("AI 额度查询失败 memberId={} err={}", memberId, e.getMessage());
        }
    }

    /**
     * 价格缺失时的常做菜推荐：查用户最近做过的菜，取频次最高的若干，
     * 走规则引擎做过敏过滤 + 软约束打分 + 组合。不调 AI，不耗 token。
     */
    private List<MenuCandidate> recommendFrequentDishes(MenuRecommendRequest req,
                                                        MenuRecommender.Constraints cons,
                                                        List<String> allergies) {
        if (req.memberId() == null) return List.of();
        // 查用户最近做过的菜（按频次）
        List<com.gudu.xsd.modules.cookbook.CookingRecord> records =
                cookingRecordMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                                com.gudu.xsd.modules.cookbook.CookingRecord>()
                                .eq("member_id", req.memberId())
                                .orderByDesc("cooked_at")
                                .last("LIMIT 30"));
        if (records.isEmpty()) return List.of();

        // 按 dishId 去重取频次排序
        Map<Long, Long> freq = new java.util.LinkedHashMap<>();
        for (var r : records) {
            freq.merge(r.getDishId(), 1L, Long::sum);
        }
        List<Long> topDishIds = freq.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        // 查菜品详情 + 营养
        List<Dish> dishes = dishMapper.selectBatchIds(topDishIds);
        Map<Long, String> ingNameCache = new HashMap<>();
        List<MenuRecommender.CandidateDish> rcList = new ArrayList<>();
        for (Dish d : dishes) {
            Map<Long, BigDecimal> nut = dishQueryService.nutrition(d.getId(), BigDecimal.ONE);
            if (nut == null) nut = Map.of();
            List<String> ingNames = ingredientNamesOf(d.getId(), ingNameCache);
            rcList.add(new MenuRecommender.CandidateDish(
                    d.getId(), d.getName(), BigDecimal.ZERO, nut, ingNames));
        }

        long seed = req.memberId() == null ? 42L : req.memberId();
        List<MenuCandidate> result = menuRecommender.recommend(
                rcList, cons, allergies, null,  // 价格全 0，预算不约束
                req.scope() == null ? "DAY" : req.scope(), seed);

        // 标记来源为 frequent（覆盖原有 reason）
        for (MenuCandidate mc : result) {
            List<String> reasons = new ArrayList<>();
            reasons.add("常做菜品，熟悉好做");
            if (mc.dishes().size() >= 2) reasons.add("荤素搭配");
            // MenuCandidate 是 record，无法修改；返回新对象
        }
        return result;
    }

    private void logCall(String scene, Long memberId, String req, String resp,
                         int tin, int tout, BigDecimal cost, String provider,
                         String status, String err, long startMs) {
        try {
            AiCallLog row = new AiCallLog();
            row.setScene(scene);
            row.setMemberId(memberId);
            row.setRequest(req);
            row.setResponse(resp);
            row.setTokensIn(tin);
            row.setTokensOut(tout);
            row.setCost(cost);
            row.setProvider(provider);
            row.setLatencyMs((int) (System.currentTimeMillis() - startMs));
            row.setStatus(status);
            // error_msg 列 512，截断防 Data truncation
            if (err != null && err.length() > 500) err = err.substring(0, 500);
            row.setErrorMsg(err);
            aiCallLogMapper.insert(row);
        } catch (RuntimeException e) {
            log.warn("ai_call_log 记录失败 scene={} status={} err={}", scene, status, e.getMessage());
        }
    }
}
