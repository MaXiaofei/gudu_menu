package com.yanhuo.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import com.yanhuo.xsd.modules.nutrition.mapper.NutritionMetricMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredientService extends ServiceImpl<IngredientMapper, Ingredient> {

    private final IngredientNutritionMapper nutMapper;
    private final NutritionMetricMapper metricMapper;

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

    /**
     * 列表（含营养）：一次查 metric 字典建 id->name 映射，再对每个食材查其 EAV，
     * 将 metricId->value 转成 metric name->value（per 100g），便于前端直接做中文映射展示。
     * 食材库量小（几十），逐条查可接受。
     */
    public List<IngredientVO> listWithNutrition() {
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        return list().stream().map(ing -> {
            IngredientVO vo = new IngredientVO();
            BeanUtils.copyProperties(ing, vo);
            Map<String, BigDecimal> named = new HashMap<>();
            for (Map.Entry<Long, BigDecimal> e : nutritionOf(ing.getId()).entrySet()) {
                String name = metricNameById.get(e.getKey());
                if (name != null) {
                    named.put(name, e.getValue());
                }
            }
            vo.setNutrition(named);
            return vo;
        }).collect(Collectors.toList());
    }
}
