package com.yanhuo.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredientService extends ServiceImpl<IngredientMapper, Ingredient> {

    private final IngredientNutritionMapper nutMapper;

    /** 保存食材，并整体替换其营养 EAV（新增/复用同一路径）。 */
    @Transactional
    public void saveWithNutrition(Ingredient ing, List<IngredientNutrition> nuts) {
        save(ing);
        nutMapper.delete(new QueryWrapper<IngredientNutrition>().eq("ingredient_id", ing.getId()));
        for (IngredientNutrition n : nuts) {
            n.setId(null);
            n.setIngredientId(ing.getId());
            nutMapper.insert(n);
        }
    }

    /** 返回该食材各营养指标 per 100g 的值：metricId -> value。 */
    public Map<Long, BigDecimal> nutritionOf(Long ingredientId) {
        return nutMapper.selectList(new QueryWrapper<IngredientNutrition>().eq("ingredient_id", ingredientId))
                .stream()
                .collect(Collectors.toMap(IngredientNutrition::getMetricId, IngredientNutrition::getValue));
    }
}
