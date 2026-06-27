package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.dish.DishService.DishDetail;
import com.gudu.xsd.modules.dish.mapper.DishHistoryMapper;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
import com.gudu.xsd.modules.nutrition.NutritionCalcService;
import com.gudu.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 菜品查询/衍生服务：历史版本快照 + 份数营养（复用 NutritionCalcService）。
 */
@Service
@RequiredArgsConstructor
public class DishQueryService {

    private final DishService dishService;
    private final DishHistoryMapper historyMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final IngredientNutritionMapper ingredientNutritionMapper;
    private final NutritionCalcService nutritionCalc;
    private final ObjectMapper objectMapper;

    /** 拉取菜品 dish_ingredient + 各食材 ingredient_nutrition，组装 Item 列表，按份数聚合营养。 */
    public Map<Long, BigDecimal> nutrition(Long dishId, BigDecimal serving) {
        List<DishIngredient> dis = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", dishId));
        if (dis.isEmpty()) return Map.of();

        List<NutritionCalcService.Item> items = new ArrayList<>();
        for (DishIngredient di : dis) {
            List<IngredientNutrition> nuts = ingredientNutritionMapper.selectList(
                    new QueryWrapper<IngredientNutrition>().eq("ingredient_id", di.getIngredientId()));
            for (IngredientNutrition n : nuts) {
                items.add(new NutritionCalcService.Item(n.getMetricId(), n.getValue(), di.getAmount()));
            }
        }
        return nutritionCalc.aggregateDish(items, serving == null ? BigDecimal.ONE : serving);
    }

    /** 列出某菜的历史版本。 */
    public List<DishHistory> history(Long dishId) {
        return historyMapper.selectList(
                new QueryWrapper<DishHistory>().eq("dish_id", dishId).orderByDesc("create_time"));
    }

    /** 删单条历史。 */
    public void deleteHistory(Long dishId, Long historyId) {
        historyMapper.delete(new QueryWrapper<DishHistory>()
                .eq("id", historyId).eq("dish_id", dishId));
    }

    /** 删某菜全部历史。 */
    public void clearHistory(Long dishId) {
        historyMapper.delete(new QueryWrapper<DishHistory>().eq("dish_id", dishId));
    }

    /** 更新前存快照：把当前详情序列化为 JSON 存入 dish_history。 */
    public void snapshotBeforeUpdate(Long dishId) {
        DishDetail detail = dishService.detail(dishId);
        if (detail.dish() == null) return;
        try {
            String json = objectMapper.writeValueAsString(detail);
            DishHistory h = new DishHistory();
            h.setDishId(dishId);
            h.setSnapshot(json);
            historyMapper.insert(h);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("快照序列化失败", e);
        }
    }
}
