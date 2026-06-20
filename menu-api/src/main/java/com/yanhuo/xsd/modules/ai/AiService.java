package com.yanhuo.xsd.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yanhuo.xsd.common.BizException;
import com.yanhuo.xsd.modules.ai.MenuRecommender.Constraints;
import com.yanhuo.xsd.modules.ai.dto.CandidateDish;
import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import com.yanhuo.xsd.modules.ai.dto.MenuRecommendRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;
import com.yanhuo.xsd.modules.ai.mapper.AiCallLogMapper;
import com.yanhuo.xsd.modules.dish.Dish;
import com.yanhuo.xsd.modules.dish.DishIngredient;
import com.yanhuo.xsd.modules.dish.DishSearchDTO;
import com.yanhuo.xsd.modules.dish.DishService;
import com.yanhuo.xsd.modules.dish.mapper.DishIngredientMapper;
import com.yanhuo.xsd.modules.dish.DishQueryService;
import com.yanhuo.xsd.modules.member.Member;
import com.yanhuo.xsd.modules.member.mapper.MemberMapper;
import com.yanhuo.xsd.modules.nutrition.Ingredient;
import com.yanhuo.xsd.modules.nutrition.IngredientNutrition;
import com.yanhuo.xsd.modules.nutrition.IngredientService;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final long METRIC_SUGAR = 5L;
    private static final long METRIC_CAL = 1L;

    // ---------------- 营养补全 ----------------

    public NutritionFillResponse fillNutrition(NutritionFillRequest req) {
        long start = System.currentTimeMillis();
        Long memberId = null; // 营养补全场景无 member 维度，预留
        java.util.Map<String, Object> reqMap = new java.util.HashMap<>();
        reqMap.put("name", req.name());
        reqMap.put("ingredientId", req.ingredientId());
        String reqJson = safeJson(reqMap);
        try {
            NutritionFillResponse resp = aiClient.fillNutrition(req);
            // 落库：ingredientId 非空时，整体替换该食材的营养 EAV（食材本身已存在，只更新营养，不重 save 防主键冲突）
            if (req.ingredientId() != null) {
                Ingredient ing = ingredientService.getById(req.ingredientId());
                if (ing != null) {
                    ingredientService.replaceNutrition(ing.getId(), resp.nutrition());
                }
            }
            logCall("nutrition_fill", memberId, reqJson, safeJson(resp),
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "ok", null, start);
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
            Map<String, Object> healthConstraints = new HashMap<>();
            if (cons.sugarMax() != null) healthConstraints.put("sugarMax", cons.sugarMax());
            if (cons.calMax() != null) healthConstraints.put("calMax", cons.calMax());
            if (!allergies.isEmpty()) healthConstraints.put("allergies", allergies);

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

            // 4. 回填候选上下文 + 健康约束进 req，交给 AiClient（DeepSeek 从候选选+组合+理由，失败降级 MenuRecommender）
            MenuRecommendRequest enriched = new MenuRecommendRequest(
                    req.memberId(), req.budget(), req.scope(),
                    req.cuisineIds(), req.tagIds(), req.categoryIds(),
                    req.maxMinutes(), req.maxDifficulty(),
                    candidates, healthConstraints);
            List<MenuCandidate> groups = aiClient.recommendMenu(enriched);

            logCall("menu_recommend", req.memberId(), reqJson, safeJson(groups),
                    0, 0, BigDecimal.ZERO, aiClient.provider(), "ok", null, start);
            return groups;
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
