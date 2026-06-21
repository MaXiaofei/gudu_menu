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
     * 批量查询多个食材的营养 EAV（一次 IN 查询，消除 N+1）。
     * 返回 ingredientId -> (metricId -> value)。未命中食材不出现在 map 中（调用方按需补空）。
     * 入参为空时直接返回空 map，避免拼出非法的 `IN ()`。
     */
    public Map<Long, Map<Long, BigDecimal>> nutritionBatch(List<Long> ingredientIds) {
        Map<Long, Map<Long, BigDecimal>> result = new HashMap<>();
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return result;
        }
        nutMapper.selectList(new QueryWrapper<IngredientNutrition>().in("ingredient_id", ingredientIds))
                .forEach(n -> result
                        .computeIfAbsent(n.getIngredientId(), k -> new HashMap<>())
                        .put(n.getMetricId(), n.getValue()));
        return result;
    }

    /**
     * 列表（含营养）：一次查 metric 字典建 id->name 映射，再对结果食材做一次 IN 批量取营养 EAV，
     * 将 metricId->value 转成 metric name->value（per 100g），便于前端直接做中文映射展示。
     * 整体 2 次 DB 查询（metric 字典 + 营养批量），不再逐食材查。
     *
     * @param purchaseCategoryId 采购分类 id，null 表示全部
     * @param keyword 名称模糊搜索关键词，null/空 表示不过滤
     */
    public List<IngredientVO> listWithNutrition(Long purchaseCategoryId, String keyword) {
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        // 权重排序：usage_count 倒序（用得多的靠前），同分再按 id 倒序稳定
        QueryWrapper<Ingredient> qw = new QueryWrapper<Ingredient>()
                .orderByDesc("usage_count").orderByDesc("id");
        if (purchaseCategoryId != null) {
            qw.eq("purchase_category_id", purchaseCategoryId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            qw.like("name", keyword.trim());
        }
        List<Ingredient> ings = list(qw);
        // 一次 IN 批量取全部营养，消除 N+1
        Map<Long, Map<Long, BigDecimal>> nutByIngredient = nutritionBatch(
                ings.stream().map(Ingredient::getId).collect(Collectors.toList()));
        return ings.stream()
                .map(ing -> toVO(ing, metricNameById,
                        nutByIngredient.getOrDefault(ing.getId(), java.util.Collections.emptyMap())))
                .collect(Collectors.toList());
    }

    /**
     * 分页列表（含营养，后台管理）：分页查食材实体后，逐条填营养 VO。
     * IPage 的 records 转成 VO 后塞回同一 IPage（total/current/size 不变）。
     * 支持 keyword(名称 LIKE) + purchaseCategoryId 双筛选；默认按 usage_count 权重倒序。
     */
    public IPage<IngredientVO> pageWithNutrition(IngredientPageQuery q) {
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        // 权重排序：usage_count 倒序（用户用得多的靠前），同分再按 id 倒序稳定
        QueryWrapper<Ingredient> qw = new QueryWrapper<Ingredient>()
                .orderByDesc("usage_count").orderByDesc("id");
        if (q.getPurchaseCategoryId() != null) {
            qw.eq("purchase_category_id", q.getPurchaseCategoryId());
        }
        if (q.getKeyword() != null && !q.getKeyword().trim().isEmpty()) {
            qw.like("name", q.getKeyword().trim());
        }
        IPage<Ingredient> page = page(new Page<>(q.getPageNum(), q.getPageSize()), qw);
        // 一次 IN 批量取本页全部营养，消除 N+1
        Map<Long, Map<Long, BigDecimal>> nutByIngredient = nutritionBatch(
                page.getRecords().stream().map(Ingredient::getId).collect(Collectors.toList()));
        List<IngredientVO> voRecords = page.getRecords().stream()
                .map(ing -> toVO(ing, metricNameById,
                        nutByIngredient.getOrDefault(ing.getId(), java.util.Collections.emptyMap())))
                .collect(Collectors.toList());
        // IPage 是接口，直接转换 records；用 setRecords 保留分页元信息
        Page<IngredientVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(voRecords);
        return result;
    }

    /**
     * 组装 VO：拷贝食材字段，把预取的 metricId->value 映射成 metric name->value。
     * 营养由调用方批量预取后传入（ingredientId 对应的那一份），不在本方法内再查 DB。
     */
    private IngredientVO toVO(Ingredient ing, Map<Long, String> metricNameById,
                              Map<Long, BigDecimal> metricIdToValue) {
        IngredientVO vo = new IngredientVO();
        BeanUtils.copyProperties(ing, vo);
        Map<String, BigDecimal> named = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> e : metricIdToValue.entrySet()) {
            String name = metricNameById.get(e.getKey());
            if (name != null) {
                named.put(name, e.getValue());
            }
        }
        vo.setNutrition(named);
        return vo;
    }
}
