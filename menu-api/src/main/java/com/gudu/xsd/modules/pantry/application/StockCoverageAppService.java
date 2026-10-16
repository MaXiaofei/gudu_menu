package com.gudu.xsd.modules.pantry.application;

import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import com.gudu.xsd.modules.pantry.domain.model.DishMaterial;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.pantry.domain.service.StockCoverageCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 覆盖率应用服务：一次请求内批量加载（食材 1 次 + 调料品类 1 次 + 档位 1 次），对多道菜内存计算，无 N+1。
 */
@Service
@RequiredArgsConstructor
public class StockCoverageAppService {

    static final String CONDIMENT_GROUP = "purchase_category";
    static final String CONDIMENT_NAME = "调味料";

    private final StockAppService stockAppService;
    private final IngredientService ingredientService;
    private final DictService dictService;
    private final StockCoverageCalculator calculator = new StockCoverageCalculator();

    /**
     * @param ingredientIdsByDish dishId → 该菜用料 ingredientId（可重复，内部去重）
     * @return dishId → 覆盖率（入参顺序）
     */
    public Map<Long, CoverageView> coverageOf(Map<Long, ? extends Collection<Long>> ingredientIdsByDish) {
        Map<Long, CoverageView> out = new LinkedHashMap<>();
        if (ingredientIdsByDish == null || ingredientIdsByDish.isEmpty()) return out;
        List<Long> allIds = ingredientIdsByDish.values().stream().filter(Objects::nonNull)
                .flatMap(Collection::stream).filter(Objects::nonNull).distinct().toList();
        Map<Long, Ingredient> ingById = allIds.isEmpty() ? Map.of()
                : ingredientService.listByIds(allIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity(), (a, b) -> a));
        Set<Long> condimentCats = dictService.idsByName(CONDIMENT_GROUP, CONDIMENT_NAME);
        Map<Long, StockLevel> levels = stockAppService.levelsOf(allIds);
        for (Map.Entry<Long, ? extends Collection<Long>> e : ingredientIdsByDish.entrySet()) {
            List<DishMaterial> materials = new ArrayList<>();
            if (e.getValue() != null) {
                for (Long ingId : e.getValue()) {
                    Ingredient ing = ingById.get(ingId);
                    if (ing == null) continue; // 食材已删：不计
                    boolean condiment = ing.getPurchaseCategoryId() != null
                            && condimentCats.contains(ing.getPurchaseCategoryId());
                    materials.add(new DishMaterial(ingId, ing.getName(), condiment));
                }
            }
            out.put(e.getKey(), CoverageView.from(calculator.calculate(materials, levels)));
        }
        return out;
    }
}
