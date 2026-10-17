package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.RecipeLine;
import com.gudu.xsd.modules.mealplan.domain.service.DishProfiler;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.NutritionMetric;
import com.gudu.xsd.modules.nutrition.NutritionMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜品目录（排餐/营养报告共用）：一次批量读全部菜 + 用料 + 食材营养 + 品类/单位字典，
 * 交给 {@link DishProfiler} 估算 1 人份营养、成本、类别。结果缓存 5 分钟（约 300 道菜，单次 &lt; 1s）。
 */
@Component
@RequiredArgsConstructor
public class DishCatalog {

    static final long CACHE_MINUTES = 5;

    private final DishService dishService;
    private final IngredientService ingredientService;
    private final NutritionMetricService metricService;
    private final DictService dictService;
    private final Clock clock;

    /** 展示信息（DishCard 用）。 */
    public record DishInfo(long id, String name, String coverUrl, Integer minutes, Integer difficulty,
                           List<String> tags, List<Long> ingredientIds) {
    }

    public record Snapshot(Map<Long, DishCandidate> candidates, Map<Long, DishInfo> info) {

        public List<DishCandidate> candidateList() {
            return new ArrayList<>(candidates.values());
        }
    }

    private volatile Snapshot cached;
    private volatile Instant loadedAt;

    public Snapshot load() {
        Instant now = clock.instant();
        Snapshot s = cached;
        if (s == null || loadedAt == null || loadedAt.plus(CACHE_MINUTES, ChronoUnit.MINUTES).isBefore(now)) {
            s = build();
            cached = s;
            loadedAt = now;
        }
        return s;
    }

    /** 测试/数据变更后强制重建。 */
    public void invalidate() {
        cached = null;
    }

    Snapshot build() {
        List<Dish> dishes = dishService.list();
        dishService.decorateCards(dishes);
        List<Long> dishIds = dishes.stream().map(Dish::getId).filter(Objects::nonNull).toList();
        Map<Long, List<DishIngredient>> linesByDish = dishService.ingredientsOf(dishIds).stream()
                .collect(Collectors.groupingBy(DishIngredient::getDishId));
        List<Long> ingIds = linesByDish.values().stream().flatMap(List::stream).map(DishIngredient::getIngredientId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Ingredient> ingById = ingIds.isEmpty() ? Map.of() : ingredientService.listByIds(ingIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity(), (a, b) -> a));
        Map<Long, Map<Long, BigDecimal>> nutrition = ingredientService.nutritionBatch(ingIds);
        Map<String, Long> metricIds = metricService.list().stream()
                .collect(Collectors.toMap(NutritionMetric::getName, NutritionMetric::getId, (a, b) -> a));
        Map<Long, String> categoryNames = dictNames("purchase_category");
        Map<Long, String> unitNames = dictNames("unit");

        Map<Long, DishCandidate> candidates = new LinkedHashMap<>();
        Map<Long, DishInfo> info = new LinkedHashMap<>();
        for (Dish d : dishes) {
            if (d.getId() == null) continue;
            List<DishIngredient> dis = linesByDish.getOrDefault(d.getId(), List.of());
            List<RecipeLine> lines = new ArrayList<>();
            Set<Long> ids = new LinkedHashSet<>();
            for (DishIngredient di : dis) {
                Ingredient ing = ingById.get(di.getIngredientId());
                if (ing == null) continue;
                ids.add(ing.getId());
                Map<Long, BigDecimal> n = nutrition.getOrDefault(ing.getId(), Map.of());
                lines.add(new RecipeLine(ing.getName(), categoryNames.get(ing.getPurchaseCategoryId()), di.getAmount(),
                        unitNames.get(di.getUnitId()), value(n, metricIds.get("calorie")),
                        value(n, metricIds.get("protein")), value(n, metricIds.get("fat"))));
            }
            Integer minutes = minutes(d);
            List<String> tags = d.getTagNames() == null ? List.of() : d.getTagNames();
            candidates.put(d.getId(), DishProfiler.candidate(d.getId(), d.getName(), minutes, Set.copyOf(tags), ids, lines));
            info.put(d.getId(), new DishInfo(d.getId(), d.getName(), d.getCoverUrl(), minutes, d.getDifficulty(),
                    tags, List.copyOf(ids)));
        }
        return new Snapshot(candidates, info);
    }

    private Map<Long, String> dictNames(String group) {
        Map<Long, String> out = new HashMap<>();
        for (SysDict sd : dictService.listByGroup(group)) out.put(sd.getId(), sd.getName());
        return out;
    }

    static Integer minutes(Dish d) {
        if (d.getPrepTime() == null && d.getCookTime() == null) return null;
        return (d.getPrepTime() == null ? 0 : d.getPrepTime()) + (d.getCookTime() == null ? 0 : d.getCookTime());
    }

    private static Double value(Map<Long, BigDecimal> n, Long metricId) {
        if (metricId == null) return null;
        BigDecimal v = n.get(metricId);
        return v == null ? null : v.doubleValue();
    }
}
