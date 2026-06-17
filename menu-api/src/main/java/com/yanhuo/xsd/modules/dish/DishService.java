package com.yanhuo.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yanhuo.xsd.modules.dish.mapper.DishDictMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishIngredientMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DishService extends ServiceImpl<DishMapper, Dish> {

    private final DishStepMapper stepMapper;
    private final DishDictMapper dictRelMapper;
    private final DishIngredientMapper dishIngMapper;

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

        // 食材用量
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

    /** 详情：菜品 + 步骤 + 菜系/标签/分类 ID + 食材用量。 */
    public DishDetail detail(Long id) {
        Dish dish = getById(id);
        List<DishStep> steps = stepMapper.selectList(
                new QueryWrapper<DishStep>().eq("dish_id", id).orderByAsc("sort_order", "seq"));
        List<DishDict> rels = dictRelMapper.selectList(new QueryWrapper<DishDict>().eq("dish_id", id));
        List<DishIngredient> ingredients = dishIngMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", id));

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
        return new DishDetail(dish, steps, cuisineIds, tagIds, categoryIds, ingredients);
    }

    public record DishDetail(Dish dish, List<DishStep> steps,
                             List<Long> cuisineIds, List<Long> tagIds, List<Long> categoryIds,
                             List<DishIngredient> ingredients) {}

    /** 多维搜索分页：keyword + 菜系/标签/分类 + 最大耗时 + 最大难度（营养上限筛选 V1 强化）。 */
    public IPage<Dish> search(DishSearchDTO q) {
        Page<Dish> page = new Page<>(q.getPageNum(), q.getPageSize());
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
        return page(page, w);
    }

    private void addRelFilter(QueryWrapper<Dish> w, List<Long> ids, String relType) {
        if (ids == null || ids.isEmpty()) return;
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        w.inSql("id", "SELECT dish_id FROM dish_dict WHERE rel_type='" + relType + "' AND dict_id IN (" + in + ")");
    }
}
