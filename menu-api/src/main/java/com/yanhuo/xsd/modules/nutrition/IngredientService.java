package com.yanhuo.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        replaceNutrition(ing.getId(), nuts);
    }

    /**
     * 仅整体替换某食材的营养 EAV（不重 save 食材本身，适用于食材已存在、仅更新营养的场景，如 AI 营养补全）。
     * 防止 saveWithNutrition 对已存在 id 的食材执行 save → 主键冲突。
     */
    @Transactional
    public void replaceNutrition(Long ingredientId, List<IngredientNutrition> nuts) {
        nutMapper.delete(new QueryWrapper<IngredientNutrition>().eq("ingredient_id", ingredientId));
        for (IngredientNutrition n : nuts) {
            n.setId(null);
            n.setIngredientId(ingredientId);
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
     *
     * @param purchaseCategoryId 采购分类 id，null 表示全部
     */
    public List<IngredientVO> listWithNutrition(Long purchaseCategoryId) {
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        QueryWrapper<Ingredient> qw = new QueryWrapper<Ingredient>().orderByDesc("id");
        if (purchaseCategoryId != null) {
            qw.eq("purchase_category_id", purchaseCategoryId);
        }
        return list(qw).stream().map(ing -> toVO(ing, metricNameById)).collect(Collectors.toList());
    }

    /**
     * 分页列表（含营养，后台管理）：分页查食材实体后，逐条填营养 VO。
     * IPage 的 records 转成 VO 后塞回同一 IPage（total/current/size 不变）。
     * 支持按 purchaseCategoryId 过滤；营养排序由前端在拉全量后处理（数据量小）。
     */
    public IPage<IngredientVO> pageWithNutrition(IngredientPageQuery q) {
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        QueryWrapper<Ingredient> qw = new QueryWrapper<Ingredient>().orderByDesc("id");
        if (q.getPurchaseCategoryId() != null) {
            qw.eq("purchase_category_id", q.getPurchaseCategoryId());
        }
        IPage<Ingredient> page = page(new Page<>(q.getPageNum(), q.getPageSize()), qw);
        List<IngredientVO> voRecords = page.getRecords().stream()
                .map(ing -> toVO(ing, metricNameById))
                .collect(Collectors.toList());
        // IPage 是接口，直接转换 records；用 setRecords 保留分页元信息
        Page<IngredientVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(voRecords);
        return result;
    }

    private IngredientVO toVO(Ingredient ing, Map<Long, String> metricNameById) {
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
    }
}
