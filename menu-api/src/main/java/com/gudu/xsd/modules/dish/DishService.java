package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.mapper.DishDictMapper;
import com.gudu.xsd.modules.dish.mapper.DishHistoryMapper;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.dish.mapper.DishStepMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
import com.gudu.xsd.modules.nutrition.NutritionCalcService;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DishService extends ServiceImpl<DishMapper, Dish> {

    /** 营养过滤前从 SQL 候选池取的最大条数，防 N+1 拖垮。 */
    static final int NUTRITION_CANDIDATE_CAP = 200;

    private final DishStepMapper stepMapper;
    private final DishDictMapper dictRelMapper;
    private final DishIngredientMapper dishIngMapper;
    private final DishHistoryMapper historyMapper;
    private final IngredientNutritionMapper ingredientNutritionMapper;
    private final IngredientMapper ingredientMapper;
    private final NutritionCalcService nutritionCalc;
    private final DictMapper dictMapper;
    private final CookingRecordMapper cookingRecordMapper;
    private final com.gudu.xsd.modules.pantry.PantryService pantryService;

    /** 保存菜品（新增或更新），整体替换步骤 + 菜系/标签/分类关联 + 食材用量。 */
    @Transactional
    public void saveFull(DishSaveDTO dto) {
        Dish dish = dto.getDish();
        saveOrUpdate(dish);
        Long dishId = dish.getId();

        // 步骤
        stepMapper.delete(new QueryWrapper<DishStep>().eq("dish_id", dishId));
        if (dto.getSteps() != null) {
            for (DishStep s : dto.getSteps()) {
                s.setId(null);
                s.setDishId(dishId);
                stepMapper.insert(s);
            }
        }

        // 字典关联（菜系/标签/分类）
        dictRelMapper.delete(new QueryWrapper<DishDict>().eq("dish_id", dishId));
        saveRels(dishId, dto.getCuisineIds(), "cuisine");
        saveRels(dishId, dto.getTagIds(), "tag");
        saveRels(dishId, dto.getCategoryIds(), "category");

        // 食材用量（V55：不再换算克数，用量原文 = amount + unitId）
        dishIngMapper.delete(new QueryWrapper<DishIngredient>().eq("dish_id", dishId));
        if (dto.getIngredients() != null) {
            for (DishIngredient ing : dto.getIngredients()) {
                ing.setId(null);
                ing.setDishId(dishId);
                dishIngMapper.insert(ing);
            }
        }
    }

    private void saveRels(Long dishId, List<Long> dictIds, String relType) {
        if (dictIds == null) return;
        for (Long dictId : dictIds) {
            DishDict r = new DishDict();
            r.setDishId(dishId);
            r.setDictId(dictId);
            r.setRelType(relType);
            dictRelMapper.insert(r);
        }
    }

    /**
     * 删除菜谱（错误数据清理，列表左滑删除）：连带物理清步骤/菜系标签分类关联/用料/编辑历史，
     * 主表软删（deleted=1）。做菜记录（cooking_record）保留——是历史行为记录，不随菜谱删除抹掉。
     */
    @Transactional
    public void deleteFull(Long id) {
        stepMapper.delete(new QueryWrapper<DishStep>().eq("dish_id", id));
        dictRelMapper.delete(new QueryWrapper<DishDict>().eq("dish_id", id));
        dishIngMapper.delete(new QueryWrapper<DishIngredient>().eq("dish_id", id));
        historyMapper.delete(new QueryWrapper<DishHistory>().eq("dish_id", id));
        removeById(id);
    }

    /** 详情：菜品 + 步骤 + 菜系/标签/分类 ID + 食材用量。 */
    public DishDetail detail(Long id) {
        Dish dish = getById(id);
        List<DishStep> steps = stepMapper.selectList(
                new QueryWrapper<DishStep>().eq("dish_id", id).orderByAsc("sort_order", "seq"));
        List<DishDict> rels = dictRelMapper.selectList(new QueryWrapper<DishDict>().eq("dish_id", id));
        List<DishIngredient> ingredients = dishIngMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", id));

        // 批量回填食材名（一次查 ingredient 表，避免前端逐个查名字的 N+1）。
        if (!ingredients.isEmpty()) {
            Set<Long> ingIds = ingredients.stream()
                    .map(DishIngredient::getIngredientId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!ingIds.isEmpty()) {
                Map<Long, String> nameMap = ingredientMapper.selectBatchIds(ingIds).stream()
                        .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName));
                for (DishIngredient di : ingredients) {
                    di.setIngredientName(nameMap.get(di.getIngredientId()));
                }
            }
        }

        // 批量回填单位名（按 unitId 查 sys_dict；「适量/少许/一小把」量词单位回显原文，§16.3）。
        if (!ingredients.isEmpty()) {
            Set<Long> unitIds = ingredients.stream()
                    .map(DishIngredient::getUnitId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!unitIds.isEmpty()) {
                Map<Long, String> unitNameMap = dictMapper.selectBatchIds(unitIds).stream()
                        .collect(Collectors.toMap(SysDict::getId, SysDict::getName));
                for (DishIngredient di : ingredients) {
                    di.setUnitName(unitNameMap.get(di.getUnitId()));
                }
            }
        }

        List<Long> cuisineIds = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        List<Long> categoryIds = new ArrayList<>();
        for (DishDict r : rels) {
            switch (r.getRelType()) {
                case "cuisine" -> cuisineIds.add(r.getDictId());
                case "tag" -> tagIds.add(r.getDictId());
                case "category" -> categoryIds.add(r.getDictId());
            }
        }
        // 批量回填库存档位（菜谱详情用料行「家里：充足/不足/用完」徽标数据源，B5）。
        if (!ingredients.isEmpty()) {
            List<Long> stockIngIds = ingredients.stream()
                    .map(DishIngredient::getIngredientId).filter(Objects::nonNull).distinct().toList();
            Map<Long, String> levelByIng = pantryService == null ? Map.of() : pantryService.levelMap(stockIngIds);
            for (DishIngredient di : ingredients) {
                di.setStockLevel(levelByIng.getOrDefault(di.getIngredientId(), "NONE"));
            }
        }

        // 回填菜系/分类/标签名，与 search 列表行为一致（避免详情页这几个字段为 null）。
        fillRelNames(List.of(dish));
        return new DishDetail(dish, steps, cuisineIds, tagIds, categoryIds, ingredients);
    }

    public record DishDetail(Dish dish, List<DishStep> steps,
                             List<Long> cuisineIds, List<Long> tagIds, List<Long> categoryIds,
                             List<DishIngredient> ingredients) {}

    /** 多维搜索分页：keyword + 菜系/标签/分类 + 最大耗时 + 最大难度（营养上限筛选 V1 强化）。 */
    public IPage<Dish> search(DishSearchDTO q) {
        QueryWrapper<Dish> w = new QueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like("name", q.getKeyword());
        }
        if (q.getMaxDifficulty() != null) {
            w.le("difficulty", q.getMaxDifficulty());
        }
        if (q.getMaxMinutes() != null) {
            w.apply("(IFNULL(prep_time,0) + IFNULL(cook_time,0)) <= {0}", q.getMaxMinutes());
        }
        addRelFilter(w, q.getCuisineIds(), "cuisine");
        addRelFilter(w, q.getTagIds(), "tag");
        addRelFilter(w, q.getCategoryIds(), "category");

        // 来源筛选
        if (q.getSource() != null && !q.getSource().isBlank()) {
            w.eq("source", q.getSource());
        }
        // 做过/收藏筛选（需要当前就餐成员）
        Long curMember = null;
        try {
            curMember = cn.dev33.satoken.stp.StpUtil.getSession().getLong("currentMemberId");
        } catch (Exception ignored) {}
        if (Boolean.TRUE.equals(q.getDone()) && curMember != null) {
            w.exists("SELECT 1 FROM cooking_record cr WHERE cr.dish_id = dish.id AND cr.member_id = " + curMember);
        }
        if (Boolean.TRUE.equals(q.getStar()) && curMember != null) {
            w.exists("SELECT 1 FROM favorite f WHERE f.dish_id = dish.id AND f.member_id = " + curMember);
        }

        // 食材筛选：按包含指定食材的菜谱过滤（交集：必须包含所有选中食材）
        if (q.getIngredientIds() != null && !q.getIngredientIds().isEmpty()) {
            int ingCount = q.getIngredientIds().size();
            String ingIds = q.getIngredientIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            // 该菜品必须包含所有选中的食材
            w.exists("SELECT 1 FROM dish_ingredient di WHERE di.dish_id = dish.id " +
                    "AND di.ingredient_id IN (" + ingIds + ") " +
                    "GROUP BY di.dish_id HAVING COUNT(DISTINCT di.ingredient_id) = " + ingCount);
        }

        // 排序：缺省=创建时间倒序。cooked=做过最多（回填后当前页内存倒序）。
        w.orderByDesc("create_time");
        boolean sortByCooked = "cooked".equalsIgnoreCase(q.getSort()) && curMember != null;

        // 无营养约束：原 SQL 分页。
        Map<Long, BigDecimal> limits = q.getNutritionLimits();
        if (limits == null || limits.isEmpty()) {
            Page<Dish> page = new Page<>(q.getPageNum(), q.getPageSize());
            IPage<Dish> result = page(page, w);
            fillRelNames(result.getRecords());
            fillCookedCount(result.getRecords(), curMember);
            if (sortByCooked) {
                result.getRecords().sort(Comparator.comparingInt(Dish::getCookedCount).reversed());
            }
            return result;
        }

        // 有营养约束：先取候选池（按 NUTRITION_CANDIDATE_CAP 截断），内存算营养二次过滤后手动分页。
        List<Dish> candidates = list(w.last("LIMIT " + NUTRITION_CANDIDATE_CAP));
        List<Dish> filtered = candidates.stream()
                .filter(d -> withinNutritionLimits(d.getId(), limits))
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = Math.min((int) ((q.getPageNum() - 1) * q.getPageSize()), total);
        int to = Math.min(from + q.getPageSize(), total);
        List<Dish> pageRecords = from < to ? filtered.subList(from, to) : Collections.emptyList();

        Page<Dish> page = new Page<>(q.getPageNum(), q.getPageSize());
        page.setTotal(total);
        page.setRecords(pageRecords);
        fillRelNames(pageRecords);
        fillCookedCount(pageRecords, curMember);
        return page;
    }

    /** 批量填充菜品的菜系/分类/标签名（一次查 dish_dict + sys_dict，避免 N+1）。 */
    private void fillRelNames(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) return;
        List<Long> dishIds = dishes.stream().map(Dish::getId).filter(Objects::nonNull).toList();
        if (dishIds.isEmpty()) return;
        List<DishDict> rels = dictRelMapper.selectList(
                new QueryWrapper<DishDict>().in("dish_id", dishIds));
        if (rels.isEmpty()) return;
        Set<Long> dictIds = rels.stream().map(DishDict::getDictId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nameMap = dictIds.isEmpty() ? Map.of()
                : dictMapper.selectBatchIds(dictIds).stream()
                .collect(Collectors.toMap(SysDict::getId, SysDict::getName));
        for (Dish d : dishes) {
            List<String> cuisines = new ArrayList<>();
            List<String> categories = new ArrayList<>();
            List<String> tags = new ArrayList<>();
            for (DishDict rel : rels) {
                if (rel.getDishId() != null && rel.getDishId().equals(d.getId())) {
                    String name = nameMap.get(rel.getDictId());
                    if (name == null) continue;
                    switch (rel.getRelType()) {
                        case "cuisine" -> cuisines.add(name);
                        case "category" -> categories.add(name);
                        case "tag" -> tags.add(name);
                        default -> {}
                    }
                }
            }
            d.setCuisineNames(cuisines);
            d.setCategoryNames(categories);
            d.setTagNames(tags);
        }
    }

    /**
     * 批量回填做过次数（按当前就餐成员统计 cooking_record）。
     * 一条 SQL 按 member_id + dish_id IN(...) GROUP BY，避免逐菜 N+1。
     * member 为 null 或无记录时回填 0。
     */
    private void fillCookedCount(List<Dish> dishes, Long memberId) {
        if (dishes == null || dishes.isEmpty()) return;
        for (Dish d : dishes) d.setCookedCount(0);
        if (memberId == null) return;
        List<Long> dishIds = dishes.stream().map(Dish::getId).filter(Objects::nonNull).toList();
        if (dishIds.isEmpty()) return;
        // selectMaps 返回聚合结果（COUNT(*) AS cnt），避免实体无 cnt 字段的映射问题
        List<Map<String, Object>> rows = cookingRecordMapper.selectMaps(
                new QueryWrapper<CookingRecord>()
                        .select("dish_id", "COUNT(*) AS cnt")
                        .eq("member_id", memberId)
                        .in("dish_id", dishIds)
                        .groupBy("dish_id"));
        Map<Long, Long> cntMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object dishIdObj = row.get("dish_id");
            Object cntObj = row.get("cnt");
            if (dishIdObj != null) {
                long cnt = cntObj == null ? 0L : ((Number) cntObj).longValue();
                cntMap.put(((Number) dishIdObj).longValue(), cnt);
            }
        }
        for (Dish d : dishes) {
            if (d.getId() != null && cntMap.containsKey(d.getId())) {
                d.setCookedCount(cntMap.get(d.getId()).intValue());
            }
        }
    }

    /** 该菜（1 份）各指标营养值是否全部 ≤ limits 上限；任一超限返回 false。 */
    private boolean withinNutritionLimits(Long dishId, Map<Long, BigDecimal> limits) {
        Map<Long, BigDecimal> nutrition = computeNutrition(dishId);
        for (Map.Entry<Long, BigDecimal> e : limits.entrySet()) {
            BigDecimal max = e.getValue();
            if (max == null) continue;
            BigDecimal v = nutrition.get(e.getKey());
            if (v != null && v.compareTo(max) > 0) return false;
        }
        return true;
    }

    /** 算单道菜 1 份营养（复用 NutritionCalcService；与 DishQueryService.nutrition 等价，避开循环依赖）。 */
    private Map<Long, BigDecimal> computeNutrition(Long dishId) {
        List<DishIngredient> dis = dishIngMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", dishId));
        if (dis.isEmpty()) return Collections.emptyMap();
        List<NutritionCalcService.Item> items = new ArrayList<>();
        for (DishIngredient di : dis) {
            List<IngredientNutrition> nuts = ingredientNutritionMapper.selectList(
                    new QueryWrapper<IngredientNutrition>().eq("ingredient_id", di.getIngredientId()));
            for (IngredientNutrition n : nuts) {
                // V55：无换算后 qty 直接取用量数字（营养本次未启用，走兜底语义）
                items.add(new NutritionCalcService.Item(n.getMetricId(), n.getValue(), di.getAmount()));
            }
        }
        return nutritionCalc.aggregateDish(items);
    }

    private void addRelFilter(QueryWrapper<Dish> w, List<Long> ids, String relType) {
        if (ids == null || ids.isEmpty()) return;
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        w.inSql("id", "SELECT dish_id FROM dish_dict WHERE rel_type='" + relType + "' AND dict_id IN (" + in + ")");
    }
}
